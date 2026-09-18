# AIOps Agent Platform - Implementation Status

This file is the durable handoff checkpoint for interrupted Codex sessions.

## Objective

Build a runnable AIOps Agent platform focused on the three resume claims:

1. Markdown-defined Skills are parsed and dynamically registered as Spring AI tools.
2. Agent State and Memory are persisted in Redis, including tool-call history.
3. A single SSE connection streams model output, tool progress, and a final PDF report link.

## Required end-to-end demonstration

1. Submit a simulated service alert.
2. Create an asynchronous diagnosis task.
3. Subscribe to the task SSE stream.
4. Plan diagnosis steps.
5. Retrieve a matching runbook.
6. Dynamically execute metric/log/release-query Skills.
7. Persist task state, evidence, events, and tool-call records in Redis.
8. Produce a diagnosis conclusion.
9. Generate a PDF report and emit `REPORT_READY` with its download URL.
10. Restore task/event state after an application restart.

## Scope decisions

- New implementation; the old SuperBizAgent project is read-only reference material.
- Modular monolith, not microservices.
- Java 17, Maven, Spring Boot, Spring AI.
- Redis is required for runtime state and memory.
- The local demo uses in-memory repositories; the Docker profile uses Redis persistence.
- A deterministic mock model is available so the complete demo works without an API key.
- Real model access uses an OpenAI-compatible configuration profile.
- RAG has a local deterministic implementation for offline use; a vector-store adapter can be added without changing the Agent API.
- SSE transports JSON events and a PDF download URL, never PDF binary data.
- Dangerous remediation actions are outside the first implementation; tools are read-only simulation tools.

## Planned checkpoints

- [x] C1: Maven project, application startup, health endpoint, configuration profiles.
- [x] C2: Diagnosis task domain model, Redis repositories, Agent Memory, tool history, and event journal.
- [x] C3: Markdown Skill parser, validation, registry, reload, three diagnostic handlers.
- [x] C4: Agent runtime, planner abstraction, mock planner, tool execution loop.
- [x] C5: SSE stream with event IDs, replay, reconnect support, heartbeat, and terminal cleanup.
- [x] C6: Runbook retrieval and evidence-bound diagnosis.
- [x] C7: PDF generation and secure report download endpoint.
- [x] C8: Demo UI and three simulated fault scenarios.
- [x] C9: Docker Compose, tests, metrics, README, audit query API, and interview guide complete.

## Current state

- Reference project inspected read-only.
- New Spring Boot project compiles successfully with Java 17.
- End-to-end offline diagnosis chain is implemented in code using in-memory repositories.
- Three Markdown Skills dynamically bind to Spring `SkillHandler` beans.
- Redis profile repositories and Agent Memory have been implemented and compile successfully.
- Local HTTP workflow verified through task completion, 36 replayable SSE events, two dynamic Tool calls, and PDF download.
- Spring AI native `ToolCallbackProvider` adapter and optional real LLM Profile implemented.
- Demo console, Dockerfile, Redis Compose stack, tests, metrics, and README implemented.
- Audit query API and a resume-oriented Chinese interview guide are implemented.
- Redis Compose restart recovery has been verified end to end; the retained volume can be reused for later demos.

## Verification log

- `mvn -s .mvn/settings.xml -q -DskipTests compile` passed on 2026-08-28.
- Compilation also passed after adding Redis State, Memory, event, and tool-call repositories.
- Local application startup passed on 2026-08-31.
- `cpu_spike` HTTP diagnosis completed and generated a valid 1,279-byte PDF.
- SSE replay returned ordered events 1 through 36, including Tool progress, model tokens, `REPORT_READY`, and completion.
- `mvn -s .mvn/settings.xml -q test` passed: 2 tests, 0 failures, 0 errors.
- The test suite passed again on 2026-09-18 after adding the audit view assertions.
- `docker compose config --quiet` passed.
- Redis Profile restart recovery passed on 2026-09-18: task `15348c87-a5bf-48a1-acfc-13743e8854bf` remained `COMPLETED` at version 8 with 2 memory records, 3 tool calls, and 42 replayable events.
- The recovered task's PDF report remained downloadable after restart (HTTP 200, `application/pdf`, 1,329 bytes).
- GitHub showcase completed on 2026-09-18: README architecture diagram, dashboard screenshot, full Agent chain, Skills, SOPs, real RAG/Tool audit sample, polished PDF report, quick start, test evidence, MIT license, environment template, and CI workflow.
