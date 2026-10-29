import json,pathlib,csv
p=pathlib.Path(__file__).resolve().parent
rows=[];runs=[];samples={}
for n in (100,1000,10000):
 r=p/'runs'/str(n);d=json.loads((r/'summary.json').read_text());checks=json.loads((r/'correctness.json').read_text())
 for op in ('list','selective','broad','empty'):
  v=d['metrics']['read_ms{operation:'+op+'}']['values'];c=next(x for x in checks if x['operation']==op)
  rows.append(dict(products=n,operation=op,median_ms=v['med'],p95_ms=v['p(95)'],returned_items=c['count'],response_bytes=c['bytes']))
 assert all(t['ok'] for m in d['metrics'].values() for t in m.get('thresholds',{}).values())
 assert all(c['unique'] and c['exact_id_membership'] and c['media_two_each'] for c in checks)
 runs.append(dict(products=n,requests=d['metrics']['http_reqs']['values']['count'],rps=d['metrics']['http_reqs']['values']['rate'],error_rate=d['metrics']['business_errors']['values']['rate'],exit_code=int((r/'exit-code.txt').read_text()),correctness_passed=True,thresholds_passed=True))
 samples[n]=len((r/'resource-samples.jsonl').read_text().splitlines())
with (p/'metrics.csv').open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
(p/'result.json').write_text(json.dumps(dict(runs=runs,metrics=rows,resource_sample_counts=samples,scope='Local ARM64 Docker, code e73fde73, 2 VUs for 45s at each size; fixed 20 sellers and two media metadata rows/product, empty order/review history. Not production throughput or maximum capacity.'),indent=2))
lines=['SWE5006 local catalogue volume test — 29 October 2026','','Result: all three 2-VU/45-second datasets completed with zero errors; configured median <1s/P95 <2s checks passed. Returned IDs, uniqueness and two media records/product matched all fixtures.','Products | list P95 ms | selective P95 ms | broad P95 ms | empty P95 ms']
for n in (100,1000,10000):lines.append(str(n)+' | '+' | '.join('{:.2f}'.format(next(x['p95_ms'] for x in rows if x['products']==n and x['operation']==op)) for op in ('list','selective','broad','empty')))
lines += [
 '',
 'Method: freshly compiled unchanged source e73fde73b42956411c95dc7145da6eb29dec778b, native ARM64. Backend 2 CPUs/1GiB; MySQL 1 CPU/1GiB; Elasticsearch 1 CPU/1GiB/256MiB heap. Dedicated compose project, local-only API port, no published database port. k6 shares the Docker host.',
 'Each dataset uses 20 sellers, two synthetic media URLs/product, price 1, stock 10, active status, identical description and timestamps. Selective query always returns 10 products; broad query and list return N; empty query returns zero. Three selective/broad warmup pairs precede each measured run. Initial unused demo listings were removed only inside the isolated local database.',
 'Requests per size: '+', '.join('{}: {}'.format(x['products'],x['requests']) for x in runs)+'. P95 samples per operation are small; repeated trials and randomized dataset order would be needed for robust performance comparisons.',
 'At 10,000 products the broad response is 10,527,474 bytes. Requests with page=0/1 and size=12 both returned all 10,000 IDs: this endpoint has no server-side pagination contract and ignores these parameters. This is a response-size limitation, not evidence of a bounded page.',
 'At 10,000 products the startup index rebuild call logged a 1,000ms timeout. The index subsequently contained all 10,000 documents and correctness checks passed. No per-request SQL-fallback warnings were observed in the retained log; per-request path tracing was not enabled. Do not equate the startup timeout with a measured request failure.',
 'Actual image objects were not uploaded or downloaded. Synthetic empty transaction/review history is not a large-history test. No conclusion about production 175/200-VU capacity follows from this local 2-VU experiment.',
 'Raw summaries, timelines, synthetic seed SQL, correctness checks, backend logs, container samples, database counters and environment image identities are retained. Local containers were stopped after testing; volumes remain for reproduction.',
 'The .env file contains locally generated credentials and is intentionally excluded from the evidence archive. Generate fresh local values before reproducing.'
]
(p/'RESULT.txt').write_text('\n'.join(lines)+'\n')
print('\n'.join(lines[:8]));print('Resource samples:',samples)
