#!/usr/bin/env python3
"""Display actual k6 results without treating Rate.fails as failed HTTP requests."""
import json
import sys
from pathlib import Path

folder = Path(sys.argv[1])
p = folder / 'summary.json'
if not p.exists():
    print('NO SUMMARY: inspect console.log; this run cannot be reported as passed.')
    sys.exit(1)
d = json.loads(p.read_text())
m = d.get('metrics', {})
meta = d.get('metadata', {})
error_values = m.get('business_errors', {}).get('values', {})
failed = int(error_values.get('passes', 0))
success = int(error_values.get('fails', 0))
count = failed + success
seconds = d.get('state', {}).get('testRunDurationMs', 0) / 1000
thresholds = [v['ok'] for metric in m.values() for v in metric.get('thresholds', {}).values() if 'ok' in v]
exitfile = folder / 'exit-code.txt'
exitcode = int(exitfile.read_text().strip()) if exitfile.exists() else None
print('\n5006 — READ-ONLY PERFORMANCE RESULT')
print('Environment:', meta.get('base', 'unknown'))
print('Profile:', meta.get('profile'), '| Virtual users:', meta.get('vus'), '| Planned load:', meta.get('duration'))
print(f'Elapsed incl. setup/drain: {seconds:.2f}s')
print(f'Load requests: {count} | Failed: {failed} | Error rate: {100*failed/count:.2f}%' if count else 'No measured load requests')
if seconds:
    print(f'Load requests / whole-run elapsed: {count/seconds:.2f} req/s')
print('Operation       Median(ms)      P95(ms)')
for op in ['list', 'search']:
    values = m.get('read_ms{operation:' + op + '}', {}).get('values', {})
    if 'med' in values and 'p(95)' in values:
        print(f'{op:12} {values["med"]:12.2f} {values["p(95)"]:12.2f}')
    else:
        print(f'{op:12} INCOMPLETE')
print('Gates: median <1000ms; P95 <2000ms; additional QA error guard <1%')
if exitcode is None:
    print('Run exit status: unavailable in this folder; verify original console completion.')
else:
    print('Run exit code:', exitcode)
print('Recorded thresholds:', 'PASS' if count and thresholds and all(thresholds) else 'FAIL / INCOMPLETE')
print('This is not verification of the 200-user capacity target.')
print('Evidence:', folder.resolve())
