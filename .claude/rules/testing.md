# 测试约定（全局生效）

## Java（JUnit 5 + Mockito + AssertJ）
- 新逻辑必须配单测；优先**纯函数/纯 Mockito**，避免 `@SpringBootTest`（除非确需上下文，且不应依赖外部 infra）。
- 纯逻辑抽成静态/包级方法便于单测（如 `HistoryCompactor`、`AgentLoopCanaryGate.bucketOf`、`parseAgentLoopFinalAnswer`）。
- 单模块跑：`mvn -pl <module> test -Dtest=ClassName`；多模块带依赖：加 `-am` + `-Dsurefire.failIfNoSpecifiedTests=false`。
- 不引入需要真实 MySQL/Redis/Nacos/Milvus/LLM 的测试到默认测试集。

## Python（ai-inference-service · unittest）
- 纯函数优先（如 `text_splitter`、`hybrid_retrieval`、`memory_store` 的 key/分桶函数）。
- 外部依赖（redis/milvus/httpx/langchain）用**延迟 import** + 降级，使无依赖环境也能跑纯逻辑单测。
- 跑：`python3 -m unittest tests.test_xxx`。

## Eval / 回归
- 风险识别离线评测：`eval/run_eval.py`（`--validate-only` 纯 stdlib 体检数据集；`--threshold` 卡等级一致率）。
- CI 门：`.github/workflows/eval-gate.yml`（validate 无条件 + threshold 条件）。

## 门禁
- 每个原子任务完成跑一次相关单测；阶段完成跑 `mvn clean install` + Python 单测全绿再继续。
