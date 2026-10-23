#!/usr/bin/env bash
# Run from any directory. Keep failures visible and still display retained evidence.
set -uo pipefail
script_dir="$(cd "$(dirname "$0")" && pwd)"
profile="${1:-demo}"
case "$profile" in smoke|demo) ;; *) echo 'Presentation supports smoke or demo only.' >&2; exit 2;; esac
: "${BASE_URL:?Set BASE_URL to the agreed 5006 environment}"
export RESULTS_DIR="$script_dir/results/$(date +%Y%m%d-%H%M%S)-live-$profile-$$"
mkdir -p "$RESULTS_DIR"
"$script_dir/run.sh" "$profile"
result=$?
printf '%s\n' "$result" > "$RESULTS_DIR/exit-code.txt"
python3 "$script_dir/show-result.py" "$RESULTS_DIR"
exit "$result"
