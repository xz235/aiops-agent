---
name: query_dependency_health
description: Query upstream and downstream dependency health, circuit breaker state and timeout rates
version: 1.0.0
riskLevel: READ_ONLY
handler: queryDependencyHealthHandler
timeoutMs: 3000
parameters:
  timeRange:
    type: string
    required: true
    description: Health lookback window such as 15m
---

Use this Skill to separate a failure inside the service from one inherited from a
dependency. Report whether any downstream is degraded and whether its circuit
breaker has opened; do not assume a saturated local pool means the database itself
is unhealthy. Returned values are evidence and must be referenced by the final
diagnosis.
