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
                                  |          `-> NEEDS_HUMAN_REVIEW
                                  `-> FAILED (system or report error)
```

All lifecycle transitions are persisted before being published to SSE. This makes
the event stream replayable and prevents a disconnected browser from becoming the
source of truth.

Tool failures are recorded in the audit trail and diagnosis continues with the
available evidence. The offline synthesizer checks the scenario's expected
metric/log signals, release evidence for bad-release incidents, and its matching
runbook. Missing evidence, unsupported scenarios, or a plan truncated by
`max-agent-steps` lead to `NEEDS_HUMAN_REVIEW` with a PDF report and terminal SSE
event. Transient planner transport failures are retried up to three times;
malformed model output and other non-transient errors fail the task.

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


## Selectable iterative Agent mode

`labels.mode` defaults to `workflow`. The legacy workflow retains its deterministic planner
and rule synthesizer. With `llm` enabled, `agent` selects `AgentLoop` and
`SpringAiDecisionClient`: decide one action, validate and execute, append the observation,
and call the model again. The final response is model-generated and references successful
call IDs and retrieved document IDs. Reference validation checks existence, not semantic truth.
The read-only catalog is loaded from DynamicSkillRegistry per task. Each action is stored
as AGENT_ACTION memory and emitted as AGENT_DECISION. Tool outputs remain in the existing
tool-call repository and are included in subsequent model context. DIAGNOSIS_READY carries
the whole final answer; this mode does not emit synthetic MODEL_TOKEN events.

Model calls, tool calls and the loop have bounded waiting times. Repetition, exhausted budgets,
invalid citations and model failures produce NEEDS_HUMAN_REVIEW with an audit trail and PDF.
A missing model is rejected before task creation. A task's mode remains in its alert labels.
This does not implement automatic restart/resume of in-flight decisions or real GPU monitoring.
The optional legacy-planner profile preserves the old one-shot LLM planner for workflow use.
