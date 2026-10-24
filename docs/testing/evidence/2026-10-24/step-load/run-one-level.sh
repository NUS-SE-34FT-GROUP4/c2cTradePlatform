#!/bin/bash
set -u
script_dir="$(cd "$(dirname "$0")" && pwd)"
vus="${1:?Specify VUs}"
case "$vus" in 10|25|50|75|100|125|150|175|200) ;; *) exit 2 ;; esac
run_dir="$script_dir/runs/$(date -u +%Y%m%dT%H%M%SZ)-${vus}vu"
mkdir -p "$run_dir"
cp "$script_dir/read-workload.js" "$run_dir/"
cd "$run_dir"
date -u +%FT%TZ > start.txt
curl -fsS --max-time 15 http://178.128.107.99:8080/actuator/info > version-before.json || exit 2
docker run --rm --user "$(id -u):$(id -g)" -v "$PWD:/results" -v "$PWD/read-workload.js:/scripts/read-workload.js:ro" -e BASE_URL=http://178.128.107.99:8080 -e VUS="$vus" -e 'KEYWORDS=QA Test Book,iPhone' grafana/k6:2.3.0 run --out json=/results/timeseries.json /scripts/read-workload.js > console.log 2>&1
result=$?
printf '%s\n' "$result" > exit-code.txt
date -u +%FT%TZ > end.txt
curl -fsS --max-time 15 http://178.128.107.99:8080/actuator/info > version-after.json
exit "$result"
