# AIOps Agent Platform

一个面向简历演示与工程学习的智能运维 Agent 平台。它把告警、知识检索、动态
Skill 调用、诊断状态、流式进度与 PDF 报告连接为一条可运行链路。

## 核心链路

```text
告警 -> Agent 规划 -> Runbook 检索 -> 动态 Skill 调用
     -> Redis State + Memory -> SSE 实时事件 -> PDF 报告
```

默认使用确定性 Mock Planner，因此没有模型 Key 也可以完整演示。启用 `llm`
Profile 后使用 Spring AI OpenAI-compatible ChatModel 生成诊断计划。

## 简历能力对应

### SkillsTool 动态扩展

`skills/*.md` 使用 YAML Front Matter 声明名称、描述、版本、风险级别、Handler
和参数。`DynamicSkillRegistry` 启动时解析并校验定义，绑定受信任的 Spring
`SkillHandler`，再适配为 Spring AI 原生 `ToolCallbackProvider`。Markdown
不能执行任意代码。

### Agent State + Memory

Redis Profile 分别保存：

- `aiops:task:{taskId}:state`：当前任务快照；
- `aiops:task:{taskId}:version`：防止旧版本覆盖；
- `aiops:task:{taskId}:events`：可重放事件；
- `aiops:task:{taskId}:memory`：RAG 证据与诊断摘要；
- `aiops:task:{taskId}:tool-calls`：工具输入、输出、耗时和状态。

任务快照使用 Lua 脚本原子校验版本，拒绝旧状态回写。

### SSE 统一事件流

`GET /api/diagnoses/{taskId}/events` 返回带事件 ID 的 SSE。支持
`Last-Event-ID` 重放、心跳、模型 Token、Tool 进度以及 `REPORT_READY`
报告下载地址。PDF 二进制通过独立下载接口传输。

## 本地启动

要求 Java 17 和 Maven 3.9+：

```powershell
mvn -s .mvn/settings.xml test
mvn -s .mvn/settings.xml package -DskipTests
java -jar target/aiops-agent-platform-0.1.0-SNAPSHOT.jar
```

访问 [http://localhost:8080](http://localhost:8080)。默认 `local` Profile 使用
内存 Repository，适合快速演示。

## Docker + Redis

```powershell
docker compose up --build
```

应用使用 `redis` Profile。重启应用容器后，任务快照、Memory、Tool 历史和
SSE 事件仍可查询。

## 使用真实模型

在 `redis` 基础上追加 `llm` Profile，并配置 OpenAI-compatible 服务：

```powershell
$env:SPRING_PROFILES_ACTIVE='redis,llm'
$env:OPENAI_API_KEY='...'
$env:OPENAI_BASE_URL='https://api.openai.com'
$env:OPENAI_MODEL='gpt-4.1-mini'
java -jar target/aiops-agent-platform-0.1.0-SNAPSHOT.jar
```

## API 示例

```http
POST /api/diagnoses
Content-Type: application/json

{
  "title": "Order API error spike",
  "service": "order-service",
  "severity": "HIGH",
  "description": "Error rate increased immediately after deployment",
  "labels": {"scenario": "bad_release"}
}
```

故障场景包括 `cpu_spike`、`connection_pool` 和 `bad_release`。

## 验证

```powershell
mvn -s .mvn/settings.xml test
```

测试覆盖 Markdown Skill 解析与完整诊断、Tool 调用及 PDF 生成流程。

