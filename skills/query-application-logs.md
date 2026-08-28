---
name: query_application_logs
description: Query representative error logs for the affected service
version: 1.0.0
riskLevel: READ_ONLY
handler: queryLogsHandler
timeoutMs: 3000
parameters:
  query:
    type: string
    required: true
    description: A constrained log query expression
---

Use this Skill after metrics indicate an anomaly. Returned samples are evidence and
must be referenced by the final diagnosis.

