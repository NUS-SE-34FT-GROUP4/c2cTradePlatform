#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "$0")" && pwd)"
profile="${1:-smoke}"
case "$profile" in smoke|demo|capacity) ;; *) echo 'Usage: run.sh smoke|demo|capacity' >&2; exit 2;; esac
: "${BASE_URL:?Set BASE_URL to the approved 5006 environment}"
results="${RESULTS_DIR:-$script_dir/results/$(date +%Y%m%d-%H%M%S)-$profile}"
mkdir -p "$results"
results="$(cd "$results" && pwd)"
printf 'Results: %s\n' "$results"
docker run --rm -i --user "$(id -u):$(id -g)" \
  -v "$script_dir:/scripts:ro" -v "$results:/results" \
  -e BASE_URL -e "PROFILE=$profile" -e "KEYWORDS=${KEYWORDS:-book,phone}" \
  grafana/k6:2.3.0 run --out json=/results/timeseries.json /scripts/read-workload.js 2>&1 | tee "$results/console.log"
