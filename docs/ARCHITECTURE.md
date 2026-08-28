# Architecture

## Runtime chain

```text
Alert API
  -> DiagnosisTaskService
  -> AgentRuntime
       -> Planner
       -> RunbookRetriever
       -> DynamicSkillRegistry
       -> SkillExecutor
       -> DiagnosisSynthesizer
       -> PdfReportService
  -> Redis State / Memory / Event Journal
  -> SSE Task Stream
```

## Module boundaries

```text
com.resume.aiops
|- alert          Alert input and simulated scenarios
|- agent          Runtime, planning, state machine, orchestration
|- skill          Markdown parser, dynamic registry, handlers
|- memory         State, memory, tool history, event journal
|- rag            Runbook ingestion and retrieval
|- streaming      SSE protocol, replay, heartbeat
|- report         PDF generation and download
|- observability  Metrics, audit, correlation IDs
`- web            REST API and exception handling
```

## Agent lifecycle

```text
CREATED -> PLANNING -> RETRIEVING -> EXECUTING_TOOLS
        -> ANALYZING -> GENERATING_REPORT -> COMPLETED
                                             `-> FAILED
```

All lifecycle transitions are persisted before being published to SSE. This makes
the event stream replayable and prevents a disconnected browser from becoming the
source of truth.

## State and memory

- State is the current mutable task snapshot.
- Memory is append-only diagnostic context: messages, evidence summaries, and tool calls.
- Events are an append-only stream with monotonically increasing IDs per task.
- A task version prevents a stale planner result from replacing a newer task state.

## Skill safety model

Markdown declares metadata and input schema. It cannot contain executable scripts.
`handler` must resolve to a pre-registered Spring bean implementing `SkillHandler`.
The registry validates names, versions, schemas, risk levels, and duplicate entries
before atomically replacing a registered definition.

## Offline-first demo

The default profile uses deterministic alert data, runbooks, and planner output.
This guarantees a repeatable end-to-end demonstration without cloud credentials.
An OpenAI-compatible planner profile can replace the mock while retaining the same
runtime contracts and validation rules.

