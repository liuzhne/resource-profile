# EduCare 离线 Eval

> **G-6 产出**：50 例风险识别 eval 集 + 跑分器，对齐 IMPROVEMENT §设计原则 5 "Eval 驱动迭代"。
> **决策**：选 promptfoo 路线（`risk_assessment.jsonl` 是其原生格式之一）但当前用自带 `run_eval.py`，因为我们要按业务自定义打分逻辑。CI 接入留 Phase H-6。

## 1. 快速开始

```bash
# 1) 起 ai-inference-service
cd ai-inference-service && uvicorn app.main:app --port 8090

# 2) 跑全集（默认 4 并发，约需 50 * LLM_单次延迟 / 4 秒）
python eval/run_eval.py
# 自定义参数：
python eval/run_eval.py \
  --input eval/risk_assessment.jsonl \
  --base-url http://localhost:8090 \
  --concurrency 4 \
  --timeout 90 \
  --out eval/run_results.json \
  --md eval/run_results.md
```

控制台会逐用例打 `[ 12/50] RA-012   OK L=low/low P=2/2`。

## 2. 文件清单

| 文件 | 用途 |
|------|------|
| `risk_assessment.jsonl` | 50 条 ground truth；每行一条 JSON 用例（schema 见 §3） |
| `run_eval.py` | 异步并发跑分器；输出 JSON + 可选 MD |
| `run_results.json` | 跑分明细（git-ignore 候选；当前未 ignore，便于 review） |
| `run_results.md` | 跑分摘要（人读，可贴 PR） |

## 3. JSONL schema（每行一条）

```json
{
  "id": "RA-001",
  "description": "短描述（review 用）",
  "input": {
    "student_profile": {
      "studentId": "S1001",
      "name": "...",
      "grade": "大三",
      "major": "...",
      "gpa": 3.85,
      "failedCourses": [],
      "attendanceRate": 0.99,
      "mentalHealthLevel": "正常|轻度|中度|重度",
      "familyEconomicLevel": "良好|中等|困难|特困",
      "counselorNotes": "...",
      "recentEvents": ["..."],
      "peerRelations": "..."
    }
  },
  "expected": {
    "risk_level": "none|low|medium|high",
    "primary_type_hints": ["学业","心理","经济","社交","其他"],
    "key_phrases": ["短语1","短语2"]
  }
}
```

字段语义：
- `input.student_profile` 直接作为 `POST /api/v1/agent/risk` 的 body（Python 端按 `RiskRequest` schema 解析，未列字段被忽略；缺字段不报错）
- `expected.risk_level` 是 ground truth 等级
- `expected.primary_type_hints` 当前**未参与**自动评分（保留给后续多标签 F1 用）
- `expected.key_phrases` 用于关键短语命中率打分（大小写不敏感 substring 在 LLM 响应文本 blob 中的命中比例）

## 4. 指标定义

| 指标 | 计算 | 用途 |
|------|------|------|
| **level_accuracy_exact** | 逐用例 `predicted_level == expected_level` 取均值 | 主指标，CI 阈值 |
| **level_score_weighted** | 完全匹配 1.0、相邻一档（none↔low / low↔medium / medium↔high）0.5、跨多档 0.0 | "几乎对"的预测有部分得分，反映可用性而非僵化对错 |
| **phrase_hit_rate** | `expected.key_phrases` 在响应 blob（`primary_risk_type + root_cause_analysis + urgency_reason + key_indicators + recommended_intervention_types` 拼接）的 substring 命中比例 | faithfulness 代理：模型有没有 *提到* 关键风险维度 |
| **by_level** | 上述三指标按 ground truth 等级分桶 | 排查偏倚：是不是某档系统性预测错（比如总把 high 预成 medium） |
| **confusion** | `(expected, predicted)` 计数 | 直接看错配方向 |

## 5. CI 接入（H-6 已落地）

workflow：`.github/workflows/eval-gate.yml`，PR 触发（paths 命中 `eval/` `ai-inference-service/` `agent-service/`）。两段式：

1. **validate-dataset（无条件跑）**：`python eval/run_eval.py --validate-only` —— 纯 stdlib 体检 50 例
   数据集完整性（id 唯一 / input 非空 / `risk_level` 合法），秒级零依赖，永远作为硬门。
2. **eval-threshold（条件跑）**：仅当仓库变量 `EVAL_LLM_ENABLED=true` 时启用（需要一个可达推理端点）。
   跑全集后按阈值门控：`--threshold 0.85`（H-6.2），可选 `--baseline eval/baseline.json` 检查回退。
   未配置端点的 fork / 默认环境只跑第 1 段，**不会因缺 LLM 误红**。

`run_eval.py` 退出码：`0`=达标且未回退；`1`=等级一致率 < `--threshold` 或相比 baseline 回退超容差。

新增 CLI 参数：
- `--validate-only`：只体检数据集，不调 LLM
- `--threshold`（默认 0.6，CI 用 0.85）：等级一致率门槛
- `--baseline <json>` + `--baseline-tolerance`（默认 0.05）：与历史 run_results 对比是否回退

启用真实门控（仓库 Settings）：
- Variables：`EVAL_LLM_ENABLED=true`
- Secrets：`EVAL_LLM_BASE_URL=<可达的 ai-inference 端点>`
- 可选：把一次可信的 `run_results.json` 复制为 `eval/baseline.json` 提交，开启回退检测

阈值演进路径：先 0.6（baseline），跑稳后逐月上调到 0.85；不要一次拉到 0.9 否则易绿失实。

## 6. 增量与维护

- **新增用例**：直接 append 一行 JSON 到 `risk_assessment.jsonl`，`id` 续号 RA-051；保持等级分布大致 `none:low:medium:high ≈ 1:2:3:2`，不要全堆 high（容易过拟合极端场景）
- **修 ground truth**：改某条的 `expected.*` 时，PR 描述写明理由（避免"为了过线而调标签"）
- **schema 变更**：若 `student_profile` 加新字段，同步更新本文件 §3 与至少 5 条样本

## 7. 已知限制

| 限制 | 处理建议 |
|------|----------|
| 50 例覆盖度有限 | 先验证流程；H 阶段后扩到 200 例 |
| substring 命中是 faithfulness 弱近似 | RAGAS / LLM-as-judge 是更强方案，但需第二个 LLM + 成本预算，留 Phase H 评估 |
| 只覆盖 risk endpoint | plan / audit endpoint 同样需要 eval 集，留 H-6 一并补 |
| 多标签 type 评分未启用 | `primary_type_hints` 字段已留好，未来加 set 交集 / Jaccard |
| 没有 LLM 温度控制 | 当前 LLM_TEMPERATURE 走服务默认；评测一致性建议固定 temp=0 跑（改 env 即可） |

## 8. 与在线 trace 的分工

- **Langfuse（G-5）**：在线全链路 trace，看真实业务调用、token、cache hit、单次诊断
- **本 eval（G-6）**：离线回归，prompt/模型/RAG 改动后的 gate；防"在线看着没问题，但 ground truth 全错"

两者互补不重复；CI 阻塞合并的是后者。
