"""Summarize measured results, leaving unreviewed semantics unscored."""
import argparse
import json
import statistics
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('results', type=Path)
p.add_argument('--reviews', type=Path, help='JSONL: id, causeCorrect, evidenceSupported, noForbiddenClaims (booleans)')
a = p.parse_args()
rows = [json.loads(x) for x in a.results.read_text(encoding='utf-8').splitlines() if x.strip()]
assert rows and len({r['id'] for r in rows}) == len(rows), 'Empty or duplicate results'
reviews = {}
if a.reviews:
    for line in a.reviews.read_text(encoding='utf-8').splitlines():
        r = json.loads(line)
        assert r['id'] not in reviews and r['id'] in {x['id'] for x in rows}
        assert all(type(r.get(k)) is bool for k in ['causeCorrect','evidenceSupported','noForbiddenClaims'])
        reviews[r['id']] = r
if any(r['mode'] != 'live' for r in rows):
    print(json.dumps({'mode':'smoke','cases':len(rows),'message':'Pipeline verification only; no model quality scores.'},ensure_ascii=False,indent=2))
else:
    report = {}
    for category in ['all'] + sorted({r['category'] for r in rows}):
        group = [r for r in rows if category=='all' or r['category']==category]
        reviewed = [reviews[r['id']] for r in group if r['id'] in reviews]
        report[category] = dict(cases=len(group),reviewDecisionAccuracy=sum(r['reviewMatch'] for r in group)/len(group),
            requiredToolsCoverage=sum(r['requiredToolsCovered'] for r in group)/len(group),
            meanToolCalls=statistics.mean(len(r['calls']) for r in group),
            medianLatencyMs=statistics.median(r['latencyMs'] for r in group),
            humanReviewed=len(reviewed),pending=len(group)-len(reviewed),
            semanticScoresOnReviewedOnly={k:sum(r[k] for r in reviewed)/len(reviewed) if reviewed else None for k in ['causeCorrect','evidenceSupported','noForbiddenClaims']})
    print(json.dumps(report,ensure_ascii=False,indent=2))
