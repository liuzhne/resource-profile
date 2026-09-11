# 增量知识导入设计（G-4.1）

> **目标**：给 `ai-inference-service` 加 `POST /api/v1/rag/upsert`，让管理员能"逐篇"导入/更新案例、政策、心理学等知识，触发 BGE 嵌入 + Milvus 写入，幂等可重入。
>
> **范围**：本期只覆盖单文档级 upsert（一次一篇）。批量导入 / 文件上传走后续。
>
> **关联**：实施落在 G-4.2，鉴权与幂等落在 G-4.3。

---

## 1. 请求 schema（Pydantic）

```python
class UpsertRequest(BaseModel):
    collection: Literal["case", "psychology", "policy", "success"]
    doc_id: str = Field(..., min_length=1, max_length=48,
                        pattern=r"^[A-Za-z0-9_\-]+$",
                        description="文档级唯一标识；用作 chunk_id 前缀。")
    text: str = Field(..., min_length=1,
                      description="完整文档纯文本；server 会按 chunk_strategy 切片。")
    metadata: dict[str, Any] = Field(default_factory=dict,
                                     description="title / source_url / tags 等，将拼入 title 字段。")
    chunk_strategy: ChunkStrategy = Field(default_factory=lambda: ChunkStrategy(name="none"))


class ChunkStrategy(BaseModel):
    name: Literal["none", "fixed_size", "sentence"]
    window: int = 800         # fixed_size 用；单 chunk 最大字符
    overlap: int = 80         # fixed_size 用；相邻 chunk 重叠字符
    max_chars: int = 1000     # sentence 用；单 chunk 上限，超长直接截断
```

字段约束理由：
- `doc_id` 限 48 字符且只允许 `A-Za-z0-9_-`：因 Milvus `chunk_id` 字段 max_length=64，要给后缀 `_{idx:04d}` 留位（48 + 1 + 4 = 53 < 64）
- `collection` 用 `Literal`：对应 `settings.MILVUS_COLLECTIONS` 的 4 个 key，无效集合直接 422

---

## 2. 响应 schema

```python
class UpsertResponse(BaseModel):
    doc_id: str
    collection: str
    chunks_written: int
    chunks_deleted_first: int  # 旧版 doc 的 chunk 数（幂等替换证据）
    embedding_ms: float
    milvus_ms: float
    skipped: bool = False      # text 哈希与现有完全一致时短路
```

---

## 3. Milvus 字段映射决策

当前 schema（`init_milvus.py:34-43`）：
| 字段 | 类型 | 本设计用途 |
|------|------|-----------|
| `id` | INT64 auto_id PK | 不用 |
| `chunk_id` | VARCHAR(64) | **`{doc_id}_{idx:04d}`**，承担 doc 分组 + 顺序 |
| `title` | VARCHAR(255) | `metadata.title \|\| "{doc_id} #{idx}"` |
| `content` | VARCHAR(4096) | 单 chunk 文本（超长 truncate 4000 字符并 warn） |
| `source` | VARCHAR(64) | `metadata.source_url` 或空 |
| `embedding` | FLOAT_VECTOR(1024) | BGE 嵌入结果 |

**关键决策**：**不**改 Milvus schema —— 用 `chunk_id` 命名约定承担 doc_id 分组。
理由：
- 改 schema = 全量重建 collection / 数据迁移，超出 G-4 范围
- 命名约定 `{doc_id}_{idx:04d}` 让删除（幂等）走 `expr` 过滤即可：`chunk_id like "DOC123_%"`
- 召回路径完全不变（chunk_id 仍是去重 key）

**已知限制**（写入文档，留 §7）：
- `metadata` 富字段（tags / 自定义 JSON）当前无字段承载，仅 title/source 入库
- chunk_id 64 字符限制 doc_id 上限 48 字符

---

## 4. 处理流程

```
1. validate（pydantic + collection 白名单）
2. chunks = split(text, chunk_strategy)
3. for each chunk: vec[i] = embed(chunks[i])
4. begin transaction(等价：先 delete 后 insert，业务可见的中间窗口）
   a. delete from collection where chunk_id like "{doc_id}_%"
   b. insert (chunk_id={doc_id}_{idx:04d}, title, content=chunks[i], source, embedding=vec[i])
5. flush + return UpsertResponse(..)
```

**为什么 delete-then-insert 而非真正的 upsert**：
- pymilvus 2.4 的 `Collection.upsert` 要 PK 已知，而我们的 PK 是 auto_id，不可控
- delete + insert 是社区推荐做法；非原子（短窗口可能召不到旧 doc）但对教育场景可接受
- 旧 chunk 数返回到响应里供调用方做"幂等真的生效"断言

---

## 5. chunk_strategy 实现摘要

| name | 行为 | 适用 |
|------|------|------|
| `none` | 一篇 = 一 chunk（content 限 4000） | 短文档 / 摘要 |
| `fixed_size` | 滑动窗口，window 字符 + overlap 字符 | 中长文档 / 案例叙事 |
| `sentence` | 按 `。!?\n\n` 切句，累加到 `max_chars` 就出 chunk | 政策法规 / 结构化条款 |

实现要点：
- 切片函数纯函数 `def split(text: str, strategy: ChunkStrategy) -> list[str]` —— 写单测覆盖三策略边界
- chunks 数上限硬编码 200（超出报 422，让调用方分多次或换 strategy），防一次性写入过载 Milvus

---

## 6. 幂等 + 鉴权（落 G-4.3）

- **doc_id 作为天然幂等键**：相同 doc_id + 相同 text hash → `skipped=True` 直接短路（不重嵌入、不写 Milvus）；text hash 用 sha256 前 16 字节，存 Redis `edu:rag:upsert:hash:{collection}:{doc_id}`，TTL 7d
- **管理员 token**：从 `Authorization: Bearer` 解 JWT roles，必须含 `admin`；否则 403
  - 实现：依赖 G-2.2-c 的 `RoleContextFilter`（Java 侧），Python 侧需要自己解 JWT —— 或者让 admin 通过专门的 `X-Admin-Token` 头（Nacos 配置环境变量）。本设计选 **`X-Admin-Token` 头**，简单粗暴，与 Java 鉴权解耦
- 限流：Sentinel 在 Java 侧；Python 侧此端点用 in-process 计数 + 1 QPS 即可（嵌入服务本来就慢）

---

## 7. 已知限制 / 不在本期范围

- **metadata 仅 title/source 入库**，tags / 自定义 JSON 当前丢失。后续如需：在 Milvus 加 `metadata_json VARCHAR(2048)` 字段（迁移 + reindex）
- **doc_id 长度上限 48**：超长建议哈希后用 12 字符
- **批量导入** + **文件上传**（CSV / Markdown 目录）—— 留下一期
- **GraphRAG（Phase I-2 储备项）**：本设计不写入 Neo4j；当 I-2 上线时再加 sidecar 流程
- **删除文档**（DELETE 端点）—— G-4 范围内不做；调用方传 `text=""` 不视为删除（会失败校验）

---

## 8. 字段名 / 路径 / 状态码约定

| 项 | 值 |
|----|----|
| 路径 | `POST /api/v1/rag/upsert` |
| Content-Type | `application/json` |
| 鉴权头 | `X-Admin-Token: ${EDUCARE_ADMIN_TOKEN}`（与 G-4.3 配套） |
| 200 | 成功，含 UpsertResponse |
| 401 | X-Admin-Token 缺失 / 不匹配 |
| 422 | pydantic 校验失败 / collection 非白名单 / chunks 超 200 |
| 500 | 嵌入服务或 Milvus 异常（含 traceback id 便于排查） |
| 503 | Milvus 连接失败（调用方应退避重试） |

---

## 9. 后续动作（G-4.2 / G-4.3 跟踪）

完成本设计后：
- **G-4.2**：实现切片函数 + delete-then-insert 流程 + 响应组装
- **G-4.3**：`X-Admin-Token` 鉴权 + doc_id sha256 短路 + 单元测试覆盖三策略

矩阵理解错就改本文件 §4 / §5，并在 §10 追加。

---

## 10. 变更记录

| 日期 | 变更 | 原因 |
|------|------|------|
| 2026-05-13 | 初版 | G-4.1 设计产出，作为 G-4.2 / 4.3 实施规格 |
