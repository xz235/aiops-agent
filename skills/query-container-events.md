---
name: query_container_events
description: Query container and instance lifecycle events such as restarts, evictions and OOM kills
version: 1.0.0
riskLevel: READ_ONLY
handler: queryContainerEventsHandler
timeoutMs: 3000
parameters:
  timeRange:
    type: string
    required: true
    description: Event lookback window such as 15m
---

Use this Skill to rule infrastructure causes in or out before blaming application
code. Restarts, OOM kills, evictions and throttling each point at a different
remedy; a clean instance record is meaningful negative evidence and should be
reported as such rather than omitted.
