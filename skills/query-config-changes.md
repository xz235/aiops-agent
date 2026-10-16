---
name: query_config_changes
description: Query recent configuration changes affecting the service
version: 1.0.0
riskLevel: READ_ONLY
handler: queryConfigChangesHandler
timeoutMs: 3000
parameters:
  timeRange:
    type: string
    required: true
    description: Configuration audit lookback window such as 2h
---

Use this Skill to decide whether an alert correlates with a code deployment or with
a configuration change. A change entry records the key, its previous and current
value, and the actor; it does not imply the change was wrong. Absence of a change is
evidence too and must be stated as such rather than filled in with a guess.
