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
- Java 21, Maven, Spring Boot, Spring AI.
- Redis is required for runtime state and memory.
- H2 is allowed for the default local demo; PostgreSQL is enabled by Docker profile.
- A deterministic mock model is available so the complete demo works without an API key.
- Real model access uses an OpenAI-compatible configuration profile.
- RAG has a local deterministic implementation for offline use; a vector-store adapter can be added without changing the Agent API.
- SSE transports JSON events and a PDF download URL, never PDF binary data.
- Dangerous remediation actions are outside the first implementation; tools are read-only simulation tools.

## Planned checkpoints

- [ ] C1: Maven project, application startup, health endpoint, configuration profiles.
- [ ] C2: Diagnosis task domain model, Redis repositories, event journal.
- [ ] C3: Markdown Skill parser, validation, registry, reload, three diagnostic handlers.
- [ ] C4: Agent runtime, planner abstraction, mock planner, tool execution loop.
- [ ] C5: SSE stream with event IDs, heartbeat, replay, and reconnect support.
- [ ] C6: Runbook retrieval and evidence-bound diagnosis.
- [ ] C7: PDF generation and secure report download endpoint.
- [ ] C8: Demo UI and three simulated fault scenarios.
- [ ] C9: Docker Compose, tests, metrics, audit logs, README, interview guide.

## Current state

- Reference project inspected read-only.
- New project directory created.
- Implementation scope frozen around the resume claims and runnable diagnosis chain.
- Next action: create C1 Maven structure and core configuration.

## Verification log

No compilation or runtime verification has been performed yet.

