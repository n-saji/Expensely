#!/usr/bin/env bash
set -euo pipefail

print_usage() {
  cat <<'EOF'
Compare latency for the expense overview endpoint across two environments.

Usage:
  scripts/perf/overview-latency.sh \
    -b <baseline_base_url> \
    -c <candidate_base_url> \
    -u <user_id> \
    [-t <jwt_token>] \
    [-n <samples_per_env>] \
    [-p <path_template>] \
    [-q <query_string>] \
    [-P <parallel_requests>] \
    [-m <timeout_seconds>]

Defaults:
  samples (-n): 50
  path (-p): /api/expenses/user/{userId}/overview
  query (-q): empty
  parallel (-P): 4
  timeout (-m): 20

Examples:
  scripts/perf/overview-latency.sh \
    -b http://localhost:8080 \
    -c http://localhost:8081 \
    -u 11111111-1111-1111-1111-111111111111 \
    -t "$JWT_TOKEN" \
    -q '?req_year=2026&req_month=4&req_month_year=2026'
EOF
}

BASELINE_URL=""
CANDIDATE_URL=""
USER_ID=""
TOKEN=""
SAMPLES=50
PATH_TEMPLATE="/api/expenses/user/{userId}/overview"
QUERY_STRING=""
PARALLEL=4
TIMEOUT=20

while getopts ":b:c:u:t:n:p:q:P:m:h" opt; do
  case "$opt" in
    b) BASELINE_URL="$OPTARG" ;;
    c) CANDIDATE_URL="$OPTARG" ;;
    u) USER_ID="$OPTARG" ;;
    t) TOKEN="$OPTARG" ;;
    n) SAMPLES="$OPTARG" ;;
    p) PATH_TEMPLATE="$OPTARG" ;;
    q) QUERY_STRING="$OPTARG" ;;
    P) PARALLEL="$OPTARG" ;;
    m) TIMEOUT="$OPTARG" ;;
    h)
      print_usage
      exit 0
      ;;
    :) echo "Missing value for -$OPTARG" >&2; print_usage; exit 1 ;;
    \?) echo "Unknown option: -$OPTARG" >&2; print_usage; exit 1 ;;
  esac
done

if [[ -z "$BASELINE_URL" || -z "$CANDIDATE_URL" || -z "$USER_ID" ]]; then
  echo "-b, -c and -u are required." >&2
  print_usage
  exit 1
fi

if ! [[ "$SAMPLES" =~ ^[0-9]+$ ]] || [[ "$SAMPLES" -lt 1 ]]; then
  echo "-n must be a positive integer." >&2
  exit 1
fi

if ! [[ "$PARALLEL" =~ ^[0-9]+$ ]] || [[ "$PARALLEL" -lt 1 ]]; then
  echo "-P must be a positive integer." >&2
  exit 1
fi

if ! [[ "$TIMEOUT" =~ ^[0-9]+$ ]] || [[ "$TIMEOUT" -lt 1 ]]; then
  echo "-m must be a positive integer." >&2
  exit 1
fi

if [[ -n "$QUERY_STRING" && "$QUERY_STRING" != \?* ]]; then
  QUERY_STRING="?$QUERY_STRING"
fi

build_full_url() {
  local base="$1"
  local path="${PATH_TEMPLATE//\{userId\}/$USER_ID}"
  printf "%s%s%s" "${base%/}" "$path" "$QUERY_STRING"
}

run_env() {
  local label="$1"
  local full_url="$2"

  local tmp_dir
  tmp_dir="$(mktemp -d)"
  local raw_file="$tmp_dir/raw.txt"
  local ok_file="$tmp_dir/ok.txt"
  local sorted_file="$tmp_dir/sorted.txt"

  echo "\n[$label] $full_url"
  echo "[$label] warmup: 3 requests"

  for _ in 1 2 3; do
    if [[ -n "$TOKEN" ]]; then
      curl -sS -m "$TIMEOUT" -o /dev/null -H "Authorization: Bearer $TOKEN" "$full_url" || true
    else
      curl -sS -m "$TIMEOUT" -o /dev/null "$full_url" || true
    fi
  done

  echo "[$label] measuring: $SAMPLES requests, parallel=$PARALLEL"

  export PERF_URL="$full_url"
  export PERF_TOKEN="$TOKEN"
  export PERF_TIMEOUT="$TIMEOUT"

  seq "$SAMPLES" | xargs -I{} -P "$PARALLEL" sh -c '
    if [ -n "$PERF_TOKEN" ]; then
      curl -sS -m "$PERF_TIMEOUT" -o /dev/null -w "%{http_code} %{time_total}\n" -H "Authorization: Bearer $PERF_TOKEN" "$PERF_URL"
    else
      curl -sS -m "$PERF_TIMEOUT" -o /dev/null -w "%{http_code} %{time_total}\n" "$PERF_URL"
    fi
  ' > "$raw_file"

  awk '$1 ~ /^2/ {print $2}' "$raw_file" > "$ok_file"

  local success_count
  success_count="$(wc -l < "$ok_file" | tr -d ' ')"
  local fail_count
  fail_count=$((SAMPLES - success_count))

  if [[ "$success_count" -eq 0 ]]; then
    echo "[$label] success=0 fail=$fail_count"
    echo "[$label] no successful requests to compute latency stats"
    rm -rf "$tmp_dir"
    return
  fi

  sort -n "$ok_file" > "$sorted_file"

  local avg p50 p95 p99 min max
  avg="$(awk '{s+=$1} END {printf "%.4f", s/NR}' "$ok_file")"
  min="$(head -n 1 "$sorted_file")"
  max="$(tail -n 1 "$sorted_file")"

  local idx50 idx95 idx99
  idx50=$(((success_count * 50 + 99) / 100))
  idx95=$(((success_count * 95 + 99) / 100))
  idx99=$(((success_count * 99 + 99) / 100))

  p50="$(awk -v i="$idx50" 'NR==i {printf "%.4f", $1; exit}' "$sorted_file")"
  p95="$(awk -v i="$idx95" 'NR==i {printf "%.4f", $1; exit}' "$sorted_file")"
  p99="$(awk -v i="$idx99" 'NR==i {printf "%.4f", $1; exit}' "$sorted_file")"

  echo "[$label] success=$success_count fail=$fail_count"
  echo "[$label] avg=${avg}s p50=${p50}s p95=${p95}s p99=${p99}s min=${min}s max=${max}s"

  rm -rf "$tmp_dir"
}

run_env "baseline" "$(build_full_url "$BASELINE_URL")"
run_env "candidate" "$(build_full_url "$CANDIDATE_URL")"

echo "\nDone. Compare p95 and p99 first; then validate avg and error counts."

