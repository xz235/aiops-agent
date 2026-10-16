---
name: query_similar_incidents
description: Query historical incidents with a matching signature and their recorded resolution
version: 1.0.0
riskLevel: READ_ONLY
handler: querySimilarIncidentsHandler
timeoutMs: 3000
parameters:
  lookback:
    type: string
    required: true
    description: Incident history lookback window such as 30d
---

Use this Skill when evidence is thin, to give the on-call reviewer a precedent
instead of an unsupported guess. A past resolution is a suggestion for a human to
evaluate, never a pre-approved action. State explicitly when no incident matches.
