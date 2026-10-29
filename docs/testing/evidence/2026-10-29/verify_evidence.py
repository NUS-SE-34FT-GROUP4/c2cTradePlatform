"""Verify preserved bytes and result consistency without contacting a server."""
import gzip
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
for entry in manifest['files']:
    path = (root / entry['path']).resolve()
    assert root in path.parents, 'Manifest path outside evidence directory'
    raw = path.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == entry['sha256'], entry['path']
    original = gzip.decompress(raw) if entry['gzip'] else raw
    assert len(original) == entry['uncompressed_bytes'], entry['path']
    assert hashlib.sha256(original).hexdigest() == entry['uncompressed_sha256'], entry['path']

for size, expected_requests in ((100, 86), (1000, 86), (10000, 76)):
    run = root / 'local-volume' / 'runs' / str(size)
    summary = json.loads((run / 'summary.json').read_text())
    assert summary['metrics']['http_reqs']['values']['count'] == expected_requests
    assert summary['metrics']['business_errors']['values']['rate'] == 0
    assert (run / 'exit-code.txt').read_text().strip() == '0'
    assert (run / 'version-before.json').read_bytes() == (run / 'version-after.json').read_bytes()
    counts = [int(x) for x in (run / 'database-counts.txt').read_text().split()]
    assert counts == [size, size * 2, 20], counts
    indexes = json.loads((run / 'index-counts.json').read_text())
    assert any(x['index'] == 'products' and int(x['docs.count']) == size for x in indexes)
    for check in json.loads((run / 'correctness.json').read_text()):
        expected = 10 if check['operation'] == 'selective' else 0 if check['operation'] == 'empty' else size
        assert check['count'] == expected
        assert all(check[x] for x in ('unique', 'exact_id_membership', 'media_two_each'))
    assert all(t['ok'] for m in summary['metrics'].values() for t in m.get('thresholds', {}).values())

recovery = root / 'load-recovery'
result = json.loads((recovery / 'result.json').read_text())
assert result['version_stable'] and result['exit_code'] == 0
assert sum(x['requests'] for x in result['results'] if x['operation'] == 'all') == 23237
assert all(x['errors'] == 0 for x in result['results'])
assert (recovery / 'version-before.json').read_bytes() == (recovery / 'version-after.json').read_bytes()
print('Verified {} retained files, three volume runs and the recovery result.'.format(len(manifest['files'])))
