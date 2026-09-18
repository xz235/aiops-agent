# AIOps Agent Platform 面试讲解

## 一分钟介绍

项目是一个基于 Spring AI 的智能运维 Agent 平台，将告警接入、Runbook
检索、动态 Tool 调用、诊断结论和 PDF 报告连接成闭环。我的重点不是做一个
聊天页面，而是实现可扩展、可恢复、可审计的 Agent Runtime。

Tool 层通过 Markdown 定义 Skill 元数据和参数 Schema，启动时动态解析并绑定
受信任的 Spring Handler，然后适配成 Spring AI 原生 `ToolCallback`。运行时
使用 Redis 分别保存任务 State、Memory、Tool 调用和 SSE 事件。前端通过一条
SSE 连接接收状态变化、Tool 进度、模型 Token 和报告下载地址。

## 核心调用链

```text
POST 告警
 -> DiagnosisTaskService 创建任务
 -> AgentRuntime 异步执行
 -> AgentPlanner 生成 Skill 计划
 -> RunbookRetriever 检索知识
 -> SkillExecutor 校验并调用动态 Skill
 -> DiagnosisSynthesizer 汇总证据
 -> PdfReportService 生成报告
 -> EventJournal 持久化事件
 -> SSE 推送并支持 Last-Event-ID 重放
```

## 为什么使用动态 Skill

直接把所有 `@Tool` 写死在 Java 代码里，会让能力说明、参数 Schema 和运行时
注册紧耦合。项目将描述、版本、风险等级和参数放在 Markdown Front Matter，
Handler 仍必须是预注册 Spring Bean。这样新增或修改能力描述不需要改 Agent
主流程，同时 Markdown 不能执行任意脚本，保留了安全边界。

## State 与 Memory 的区别

- State 是当前可变任务快照，包括状态、计划、版本、结论和报告 ID。
- Memory 是追加式上下文，包括 RAG 证据摘要与诊断结论。
- Tool History 单独记录参数、结果、成功状态和耗时。
- Event Journal 保存面向 SSE 的完整时间线。

拆开存储是为了避免将所有数据塞进一个大 JSON，也便于审计和按用途设置保留策略。

## Redis 如何防止旧状态覆盖

任务状态带单调递增版本号。保存时执行 Lua 脚本，原子读取 Redis 当前版本；
若待写版本更旧则拒绝，否则同时更新快照与版本。Lua 避免了客户端执行
`GET -> 判断 -> SET` 时的并发竞态。

## SSE 为什么不直接传 PDF

SSE 适合 UTF-8 文本事件，不适合传输大型二进制。报告生成后发送
`REPORT_READY`，其中包含 report ID 和下载 URL；PDF 使用独立 HTTP 响应。
事件带递增 ID，客户端重连时通过 `Last-Event-ID` 补发遗漏事件。

## 模型不可用怎么办

默认 Profile 使用确定性 Mock Planner，所以没有云端 Key 也能跑通整条链路。
`llm` Profile 才创建真实 Spring AI ChatModel。生产设计中，模型超时后保留
已收集证据，将任务标为失败或待人工处理；Tool 结果与审计记录不会丢失。

## 如何降低幻觉

1. LLM 只负责计划，不直接执行任意代码。
2. 可调用 Skill 来自白名单注册表。
3. Tool 参数先经过 Schema 和业务校验。
4. 结论基于 Tool 返回与 Runbook 引用。
5. 高风险处置不在当前自动执行范围内。

## 目前的工程取舍

- 使用模块化单体，避免为了展示而拆空壳微服务。
- 默认 RAG 是可重复的本地关键词检索，接口允许替换成向量检索。
- 演示工具返回确定性故障数据；真实 Prometheus、日志平台和发布系统可通过
  新 Handler 接入，不改变 Runtime。
- 当前自动化范围是只读诊断与报告，不包含自动重启、扩容或回滚。

## 常见追问

### 为什么 Tool 注册表需要原子替换？

重载过程中如果逐条修改共享 Map，Agent 可能看到半新半旧的注册表。实现先在
临时 Map 中完成全部解析、重名校验和 Handler 绑定，全部成功后一次替换引用。

### 多个任务是否相互干扰？

每个任务使用独立 `taskId` 作为 Redis Key 作用域，State、Memory、Tool History
和 Event Journal 均隔离。单个任务内部通过版本号防止旧状态回写。

### 为什么没有把 Tool 原始结果全部放进 Prompt？

原始结果写入 Tool History，用于审计；Memory 只保留摘要和引用，避免上下文
无限膨胀。真实接入中还会对日志结果做截断、脱敏与分页。

