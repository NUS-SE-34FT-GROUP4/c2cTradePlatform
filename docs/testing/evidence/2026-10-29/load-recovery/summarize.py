import json, pathlib, statistics, collections, datetime
root=pathlib.Path(__file__).resolve().parent
data=json.loads((root/'summary.json').read_text())
latency=collections.defaultdict(list)
errors=collections.Counter()
counts=collections.Counter()
for line in (root/'timeseries.json').open():
    record=json.loads(line)
    if record.get('type')!='Point': continue
    value=record['data']; tags=value.get('tags',{})
    phase=tags.get('phase'); operation=tags.get('operation')
    if not phase or phase=='preflight': continue
    for key in [(phase,'all'),(phase,operation)]:
        if record['metric']=='read_ms': latency[key].append(value['value'])
        if record['metric']=='business_errors':
            counts[key]+=1;errors[key]+=value['value']
def percentile(values,q):
    a=sorted(values); ix=(len(a)-1)*q; lo=int(ix); hi=min(lo+1,len(a)-1)
    return a[lo]+(a[hi]-a[lo])*(ix-lo)
rows=[]
for (phase,operation),values in sorted(latency.items()):
    rows.append(dict(phase=phase,operation=operation,requests=counts[phase,operation],errors=int(errors[phase,operation]),error_percent=100*errors[phase,operation]/counts[phase,operation],median_ms=statistics.median(values),p95_ms=percentile(values,.95),max_ms=max(values)))
before=json.loads((root/'version-before.json').read_text()); after=json.loads((root/'version-after.json').read_text())
result=dict(version_before=before,version_after=after,version_stable=before==after,exit_code=int((root/'exit-code.txt').read_text()),started=(root/'start.txt').read_text(),ended=(root/'end.txt').read_text(),results=rows,thresholds={k:v['thresholds'] for k,v in data['metrics'].items() if 'thresholds' in v},note='New gradual read-only workload; not a rerun of the historical independent constant-VU starts. No 200-VU or volume-test claim.')
(root/'result.json').write_text(json.dumps(result,indent=2))
lines=['SWE5006 gradual load and recovery test — 29 October 2026',str(before),'Version stable: '+str(before==after),'Exit code: '+str(result['exit_code']),'','Phase / operation | requests | errors | median ms | P95 ms']
for row in rows:lines.append(f"{row['phase']} / {row['operation']} | {row['requests']} | {row['errors']} ({row['error_percent']:.3f}%) | {row['median_ms']:.2f} | {row['p95_ms']:.2f}")
lines+=['',result['note'],'The experiment does not establish maximum sustained capacity, a failure threshold or long-duration reliability.','Security remediation and isolated-dataset volume validation are separate activities.']
(root/'RESULT.txt').write_text('\n'.join(lines)+'\n')
print('\n'.join(lines))
