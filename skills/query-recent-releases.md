---
name: query_recent_releases
description: Query recent deployments for the affected service
version: 1.0.0
riskLevel: READ_ONLY
handler: queryReleasesHandler
timeoutMs: 3000
parameters:
  timeRange:
    type: string
    required: true
    description: Deployment lookback window
---

Use this Skill to correlate an alert with a recent application release.

