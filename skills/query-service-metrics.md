---
name: query_service_metrics
description: Query recent service saturation and latency metrics
version: 1.0.0
riskLevel: READ_ONLY
handler: queryMetricsHandler
timeoutMs: 3000
parameters:
  timeRange:
    type: string
    required: true
    description: Metrics lookback window such as 15m
---

Use this Skill to establish whether CPU, latency, or a connection pool is saturated.

