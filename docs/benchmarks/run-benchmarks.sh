#!/usr/bin/env bash
# =============================================================================
# SecureAI Gateway - Performance Benchmark Suite
# =============================================================================
# Measures latency (p50, p95, p99) and throughput across all gateway layers.
# Requires: curl, bc, jq (optional for JSON parsing)
#
# Usage:
#   ./run-benchmarks.sh                  # Full suite
#   ./run-benchmarks.sh --iterations 20  # Custom iteration count
#   ./run-benchmarks.sh --skip-throughput # Skip concurrency tests
#   ./run-benchmarks.sh --help
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
GATEWAY_BASE="http://localhost:8100"
PRESIDIO_BASE="http://localhost:5002"
NEMO_BASE="http://localhost:8001"

ITERATIONS=50
CONCURRENCY_LEVELS=(1 5 10)
SKIP_THROUGHPUT=false

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CSV_FILE="${SCRIPT_DIR}/benchmark-results.csv"
HTML_FILE="${SCRIPT_DIR}/BENCHMARK_REPORT.html"
TIMESTAMP="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
TIMESTAMP_HUMAN="$(date '+%Y-%m-%d %H:%M:%S %Z')"

# Credentials for authenticated endpoints
AUTH_USER="benchuser_$$@test.com"
AUTH_PASS="Bench!Pass123"
JWT_TOKEN=""

# Colours for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# ---------------------------------------------------------------------------
# CLI argument parsing
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --iterations|-n) ITERATIONS="$2"; shift 2 ;;
        --skip-throughput) SKIP_THROUGHPUT=true; shift ;;
        --help|-h)
            echo "Usage: $0 [--iterations N] [--skip-throughput] [--help]"
            exit 0
            ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# ---------------------------------------------------------------------------
# Utility functions
# ---------------------------------------------------------------------------
log()  { echo -e "${CYAN}[BENCH]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; }
ok()   { echo -e "${GREEN}[OK]${NC} $*"; }

# Check if a service is reachable (2s timeout)
check_service() {
    local url="$1"
    local name="$2"
    if curl -sf -o /dev/null --max-time 2 "$url" 2>/dev/null; then
        ok "$name is reachable"
        return 0
    else
        warn "$name is NOT reachable at $url -- will skip"
        return 1
    fi
}

# Measure a single request latency in milliseconds (float)
measure_ms() {
    local method="$1"
    local url="$2"
    local data="${3:-}"
    local extra_headers="${4:-}"

    local curl_args=(-s -o /dev/null -w '%{time_total}' --max-time 30)
    curl_args+=(-X "$method")

    if [[ -n "$extra_headers" ]]; then
        while IFS='' read -r hdr; do
            [[ -n "$hdr" ]] && curl_args+=(-H "$hdr")
        done <<< "$extra_headers"
    fi

    if [[ -n "$data" ]]; then
        curl_args+=(-H "Content-Type: application/json" -d "$data")
    fi

    curl_args+=("$url")

    local total_s
    total_s=$(curl "${curl_args[@]}" 2>/dev/null || echo "0")
    # Convert seconds to ms
    echo "$total_s * 1000" | bc 2>/dev/null || echo "0"
}

# Run N iterations, collect latencies into a global array LATENCIES
declare -a LATENCIES
run_iterations() {
    local method="$1"
    local url="$2"
    local data="${3:-}"
    local extra_headers="${4:-}"
    local n="$ITERATIONS"

    LATENCIES=()
    local i
    for ((i = 1; i <= n; i++)); do
        local ms
        ms=$(measure_ms "$method" "$url" "$data" "$extra_headers")
        LATENCIES+=("$ms")
        # progress indicator every 10 iterations
        if (( i % 10 == 0 )); then
            printf "."
        fi
    done
    echo ""  # newline after dots
}

# Sort the LATENCIES array numerically and compute percentiles
# Sets: P50, P95, P99, AVG, MIN, MAX
compute_percentiles() {
    local n=${#LATENCIES[@]}
    if (( n == 0 )); then
        P50=0; P95=0; P99=0; AVG=0; MIN=0; MAX=0
        return
    fi

    # Sort numerically
    local sorted
    sorted=($(printf '%s\n' "${LATENCIES[@]}" | sort -g))

    # Percentile indices (0-based)
    local i50 i95 i99
    i50=$(echo "($n * 50 / 100)" | bc)
    i95=$(echo "($n * 95 / 100)" | bc)
    i99=$(echo "($n * 99 / 100)" | bc)
    # Clamp to valid range
    (( i50 >= n )) && i50=$((n - 1))
    (( i95 >= n )) && i95=$((n - 1))
    (( i99 >= n )) && i99=$((n - 1))

    P50="${sorted[$i50]}"
    P95="${sorted[$i95]}"
    P99="${sorted[$i99]}"
    MIN="${sorted[0]}"
    MAX="${sorted[$((n - 1))]}"

    # Average
    local sum=0
    for v in "${sorted[@]}"; do
        sum=$(echo "$sum + $v" | bc)
    done
    AVG=$(echo "scale=2; $sum / $n" | bc)
}

# Measure throughput: requests/second under C concurrent users for D seconds
measure_throughput() {
    local method="$1"
    local url="$2"
    local data="${3:-}"
    local extra_headers="${4:-}"
    local concurrency="$5"
    local duration=5  # seconds

    # Simple approach: fire concurrent curls in background, count completions
    local start_time end_time count
    count=0
    start_time=$(date +%s%N)

    local pids=()
    local end_epoch=$(( $(date +%s) + duration ))

    while (( $(date +%s) < end_epoch )); do
        for ((c = 0; c < concurrency; c++)); do
            (
                local curl_args=(-s -o /dev/null --max-time 10 -X "$method")
                if [[ -n "$extra_headers" ]]; then
                    while IFS='' read -r hdr; do
                        [[ -n "$hdr" ]] && curl_args+=(-H "$hdr")
                    done <<< "$extra_headers"
                fi
                if [[ -n "$data" ]]; then
                    curl_args+=(-H "Content-Type: application/json" -d "$data")
                fi
                curl_args+=("$url")
                curl "${curl_args[@]}" 2>/dev/null
            ) &
            pids+=($!)
        done
        # Wait for this batch
        for pid in "${pids[@]}"; do
            wait "$pid" 2>/dev/null && ((count++)) || true
        done
        pids=()
    done

    end_time=$(date +%s%N)
    local elapsed_ms=$(( (end_time - start_time) / 1000000 ))
    local elapsed_s
    elapsed_s=$(echo "scale=3; $elapsed_ms / 1000" | bc)

    if (( $(echo "$elapsed_s > 0" | bc -l) )); then
        echo "scale=1; $count / $elapsed_s" | bc
    else
        echo "0"
    fi
}

# ---------------------------------------------------------------------------
# Setup: register + login to get JWT
# ---------------------------------------------------------------------------
setup_auth() {
    log "Setting up test user for authenticated endpoints..."

    # Register (may fail if user exists -- that's fine)
    curl -sf -X POST "${GATEWAY_BASE}/auth/register" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"${AUTH_USER}\",\"password\":\"${AUTH_PASS}\",\"name\":\"Benchmark User\"}" \
        -o /dev/null 2>/dev/null || true

    # Login to get JWT
    local resp
    resp=$(curl -sf -X POST "${GATEWAY_BASE}/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"${AUTH_USER}\",\"password\":\"${AUTH_PASS}\"}" 2>/dev/null || echo "{}")

    JWT_TOKEN=$(echo "$resp" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")

    if [[ -z "$JWT_TOKEN" ]]; then
        warn "Could not obtain JWT token. Authenticated endpoints will be tested without auth."
    else
        ok "JWT token obtained (${#JWT_TOKEN} chars)"
    fi
}

# ---------------------------------------------------------------------------
# Endpoint definitions
# ---------------------------------------------------------------------------
# Each endpoint: NAME|METHOD|URL|BODY|EXTRA_HEADERS|LAYER
declare -a ENDPOINTS=()

define_endpoints() {
    local auth_hdr=""
    [[ -n "$JWT_TOKEN" ]] && auth_hdr="Authorization: Bearer ${JWT_TOKEN}"

    ENDPOINTS=(
        "actuator/health|GET|${GATEWAY_BASE}/actuator/health|||Baseline"
        "auth/login|POST|${GATEWAY_BASE}/auth/login|{\"email\":\"${AUTH_USER}\",\"password\":\"${AUTH_PASS}\"}||Auth (BCrypt)"
        "auth/register|POST|${GATEWAY_BASE}/auth/register|{\"email\":\"bench_reg_\${RANDOM}@test.com\",\"password\":\"${AUTH_PASS}\",\"name\":\"Reg Test\"}||Auth (Register)"
        "api/ask (simple)|POST|${GATEWAY_BASE}/api/ask|{\"prompt\":\"What is 2+2?\"}|${auth_hdr}|Full Pipeline"
        "api/ask (ReAct)|POST|${GATEWAY_BASE}/api/ask|{\"prompt\":\"Search for recent AI news and summarize\",\"useReActAgent\":true}|${auth_hdr}|ReAct Agent"
        "api/status|GET|${GATEWAY_BASE}/api/status|${auth_hdr:+|}${auth_hdr}||Rate Limiter"
        "admin/dashboard|GET|${GATEWAY_BASE}/admin/dashboard||${auth_hdr}|RBAC"
        "Presidio /analyze|POST|${PRESIDIO_BASE}/analyze|{\"text\":\"My SSN is 123-45-6789\",\"language\":\"en\"}||PII Detection"
        "NeMo /v1/health|GET|${NEMO_BASE}/v1/health|||Guardrails Health"
    )
}

# ---------------------------------------------------------------------------
# Main benchmark loop
# ---------------------------------------------------------------------------
declare -a RESULTS=()  # "name|p50|p95|p99|avg|min|max|layer"

run_latency_benchmarks() {
    log "Running latency benchmarks (${ITERATIONS} iterations each)..."
    echo ""

    for entry in "${ENDPOINTS[@]}"; do
        IFS='|' read -r name method url body extra_headers layer <<< "$entry"

        # Check reachability
        local check_url
        check_url=$(echo "$url" | grep -oP 'https?://[^/]+' || echo "$url")
        if ! curl -sf -o /dev/null --max-time 2 "$url" 2>/dev/null; then
            # Try just the host
            if ! curl -sf -o /dev/null --max-time 2 "${check_url}/" 2>/dev/null; then
                warn "Skipping ${name} -- service unreachable"
                RESULTS+=("${name}|N/A|N/A|N/A|N/A|N/A|N/A|${layer}")
                continue
            fi
        fi

        printf "${BOLD}  %-30s${NC} " "$name"
        run_iterations "$method" "$url" "$body" "$extra_headers"
        compute_percentiles

        printf "    p50=%.1fms  p95=%.1fms  p99=%.1fms  avg=%.1fms\n" "$P50" "$P95" "$P99" "$AVG"
        RESULTS+=("${name}|${P50}|${P95}|${P99}|${AVG}|${MIN}|${MAX}|${layer}")
    done
}

# ---------------------------------------------------------------------------
# Throughput benchmarks
# ---------------------------------------------------------------------------
declare -a THROUGHPUT_RESULTS=()  # "name|c1_rps|c5_rps|c10_rps"

run_throughput_benchmarks() {
    if $SKIP_THROUGHPUT; then
        log "Skipping throughput benchmarks (--skip-throughput)"
        return
    fi

    log "Running throughput benchmarks (5s per concurrency level)..."
    echo ""

    # Only test key endpoints for throughput
    local tp_endpoints=(
        "actuator/health|GET|${GATEWAY_BASE}/actuator/health||"
        "auth/login|POST|${GATEWAY_BASE}/auth/login|{\"email\":\"${AUTH_USER}\",\"password\":\"${AUTH_PASS}\"}|"
        "api/ask (simple)|POST|${GATEWAY_BASE}/api/ask|{\"prompt\":\"What is 2+2?\"}|Authorization: Bearer ${JWT_TOKEN}"
    )

    for entry in "${tp_endpoints[@]}"; do
        IFS='|' read -r name method url body extra_headers <<< "$entry"

        if ! curl -sf -o /dev/null --max-time 2 "$url" 2>/dev/null; then
            warn "Skipping throughput for ${name}"
            THROUGHPUT_RESULTS+=("${name}|N/A|N/A|N/A")
            continue
        fi

        local rps_values=()
        for c in "${CONCURRENCY_LEVELS[@]}"; do
            printf "  %-30s concurrency=%d ... " "$name" "$c"
            local rps
            rps=$(measure_throughput "$method" "$url" "$body" "$extra_headers" "$c")
            printf "%.1f req/s\n" "$rps"
            rps_values+=("$rps")
        done

        THROUGHPUT_RESULTS+=("${name}|${rps_values[0]}|${rps_values[1]}|${rps_values[2]}")
    done
}

# ---------------------------------------------------------------------------
# Output: ASCII table
# ---------------------------------------------------------------------------
print_ascii_table() {
    echo ""
    echo "=============================================================================="
    echo " SecureAI Gateway - Latency Benchmark Results"
    echo " Timestamp: ${TIMESTAMP_HUMAN}"
    echo " Iterations: ${ITERATIONS} per endpoint"
    echo "=============================================================================="
    printf "%-28s %8s %8s %8s %8s %s\n" "Endpoint" "p50(ms)" "p95(ms)" "p99(ms)" "Avg(ms)" "Layer"
    echo "------------------------------------------------------------------------------"

    for r in "${RESULTS[@]}"; do
        IFS='|' read -r name p50 p95 p99 avg min max layer <<< "$r"
        if [[ "$p50" == "N/A" ]]; then
            printf "%-28s %8s %8s %8s %8s %s\n" "$name" "N/A" "N/A" "N/A" "N/A" "$layer"
        else
            printf "%-28s %8.1f %8.1f %8.1f %8.1f %s\n" "$name" "$p50" "$p95" "$p99" "$avg" "$layer"
        fi
    done
    echo "=============================================================================="

    if [[ ${#THROUGHPUT_RESULTS[@]} -gt 0 ]]; then
        echo ""
        echo "  Throughput (req/s)"
        echo "  -----------------------------------------------------------"
        printf "  %-28s %8s %8s %8s\n" "Endpoint" "C=1" "C=5" "C=10"
        echo "  -----------------------------------------------------------"
        for r in "${THROUGHPUT_RESULTS[@]}"; do
            IFS='|' read -r name c1 c5 c10 <<< "$r"
            printf "  %-28s %8s %8s %8s\n" "$name" "$c1" "$c5" "$c10"
        done
        echo "  -----------------------------------------------------------"
    fi
    echo ""
}

# ---------------------------------------------------------------------------
# Output: CSV
# ---------------------------------------------------------------------------
write_csv() {
    log "Writing CSV to ${CSV_FILE}"
    {
        echo "timestamp,endpoint,p50_ms,p95_ms,p99_ms,avg_ms,min_ms,max_ms,layer"
        for r in "${RESULTS[@]}"; do
            IFS='|' read -r name p50 p95 p99 avg min max layer <<< "$r"
            echo "${TIMESTAMP},${name},${p50},${p95},${p99},${avg},${min},${max},${layer}"
        done
    } > "$CSV_FILE"
    ok "CSV written: ${CSV_FILE}"
}

# ---------------------------------------------------------------------------
# Output: HTML report
# ---------------------------------------------------------------------------
generate_html_report() {
    log "Generating HTML report at ${HTML_FILE}"

    # Build table rows
    local table_rows=""
    local chart_bars=""
    local bar_index=0
    local max_p99=0

    # Find max p99 for chart scaling
    for r in "${RESULTS[@]}"; do
        IFS='|' read -r name p50 p95 p99 avg min max layer <<< "$r"
        if [[ "$p99" != "N/A" ]]; then
            local cmp
            cmp=$(echo "$p99 > $max_p99" | bc 2>/dev/null || echo "0")
            if (( cmp == 1 )); then
                max_p99="$p99"
            fi
        fi
    done
    # Minimum scale
    (( $(echo "$max_p99 < 100" | bc) )) && max_p99=100

    for r in "${RESULTS[@]}"; do
        IFS='|' read -r name p50 p95 p99 avg min max layer <<< "$r"

        if [[ "$p50" == "N/A" ]]; then
            table_rows+="<tr class='skipped'><td>${name}</td><td colspan='5'>Service unavailable</td><td>${layer}</td></tr>"
        else
            table_rows+="<tr><td>${name}</td><td>${p50}</td><td>${p95}</td><td>${p99}</td><td>${avg}</td><td>${min} / ${max}</td><td>${layer}</td></tr>"

            # Chart bars (percentage of max)
            local pct50 pct95 pct99
            pct50=$(echo "scale=1; $p50 * 100 / $max_p99" | bc 2>/dev/null || echo "0")
            pct95=$(echo "scale=1; $p95 * 100 / $max_p99" | bc 2>/dev/null || echo "0")
            pct99=$(echo "scale=1; $p99 * 100 / $max_p99" | bc 2>/dev/null || echo "0")

            chart_bars+="
            <div class='chart-row'>
                <div class='chart-label'>${name}</div>
                <div class='chart-bars'>
                    <div class='bar p50' style='width:${pct50}%'><span>${p50}ms</span></div>
                    <div class='bar p95' style='width:${pct95}%'><span>${p95}ms</span></div>
                    <div class='bar p99' style='width:${pct99}%'><span>${p99}ms</span></div>
                </div>
            </div>"
        fi

        ((bar_index++))
    done

    # Throughput rows
    local throughput_rows=""
    for r in "${THROUGHPUT_RESULTS[@]}"; do
        IFS='|' read -r name c1 c5 c10 <<< "$r"
        throughput_rows+="<tr><td>${name}</td><td>${c1}</td><td>${c5}</td><td>${c10}</td></tr>"
    done

    # LaTeX table
    local latex_rows=""
    for r in "${RESULTS[@]}"; do
        IFS='|' read -r name p50 p95 p99 avg min max layer <<< "$r"
        local escaped_name
        escaped_name=$(echo "$name" | sed 's/_/\\_/g; s/&/\\&/g')
        if [[ "$p50" == "N/A" ]]; then
            latex_rows+="        ${escaped_name} & -- & -- & -- & -- & ${layer} \\\\\\\\\n"
        else
            latex_rows+="        ${escaped_name} & ${p50} & ${p95} & ${p99} & ${avg} & ${layer} \\\\\\\\\n"
        fi
    done

    cat > "$HTML_FILE" << 'HTMLEOF'
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>SecureAI Gateway - Performance Benchmark Report</title>
<style>
:root {
    --bg-primary: #0d1117;
    --bg-secondary: #161b22;
    --bg-tertiary: #21262d;
    --border: #30363d;
    --text-primary: #e6edf3;
    --text-secondary: #8b949e;
    --accent-blue: #58a6ff;
    --accent-green: #3fb950;
    --accent-orange: #d29922;
    --accent-red: #f85149;
    --accent-purple: #bc8cff;
    --p50-color: #3fb950;
    --p95-color: #d29922;
    --p99-color: #f85149;
}

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', Helvetica, Arial, sans-serif;
    background: var(--bg-primary);
    color: var(--text-primary);
    line-height: 1.6;
    padding: 2rem;
}

.container { max-width: 1200px; margin: 0 auto; }

h1 {
    font-size: 2rem;
    margin-bottom: 0.5rem;
    background: linear-gradient(135deg, var(--accent-blue), var(--accent-purple));
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
}

h2 {
    font-size: 1.4rem;
    color: var(--accent-blue);
    margin: 2rem 0 1rem;
    padding-bottom: 0.5rem;
    border-bottom: 1px solid var(--border);
}

h3 { font-size: 1.1rem; color: var(--text-secondary); margin: 1rem 0 0.5rem; }

.meta {
    color: var(--text-secondary);
    font-size: 0.9rem;
    margin-bottom: 2rem;
}
.meta span { margin-right: 2rem; }
.meta .tag {
    display: inline-block;
    background: var(--bg-tertiary);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 2px 10px;
    font-size: 0.8rem;
}

/* Tables */
table {
    width: 100%;
    border-collapse: collapse;
    margin: 1rem 0;
    font-size: 0.9rem;
}

th, td {
    padding: 10px 14px;
    text-align: left;
    border-bottom: 1px solid var(--border);
}

th {
    background: var(--bg-secondary);
    color: var(--text-secondary);
    font-weight: 600;
    text-transform: uppercase;
    font-size: 0.75rem;
    letter-spacing: 0.05em;
}

td { font-variant-numeric: tabular-nums; }

tr:hover { background: var(--bg-secondary); }
tr.skipped td { color: var(--text-secondary); font-style: italic; }

.val-p50 { color: var(--p50-color); font-weight: 600; }
.val-p95 { color: var(--p95-color); font-weight: 600; }
.val-p99 { color: var(--p99-color); font-weight: 600; }

/* Chart */
.chart-container {
    background: var(--bg-secondary);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 1.5rem;
    margin: 1rem 0;
}

.chart-legend {
    display: flex;
    gap: 1.5rem;
    margin-bottom: 1rem;
    font-size: 0.85rem;
}

.legend-item { display: flex; align-items: center; gap: 6px; }
.legend-dot {
    width: 12px; height: 12px; border-radius: 3px;
}
.legend-dot.p50 { background: var(--p50-color); }
.legend-dot.p95 { background: var(--p95-color); }
.legend-dot.p99 { background: var(--p99-color); }

.chart-row {
    display: flex;
    align-items: center;
    margin-bottom: 12px;
}

.chart-label {
    width: 200px;
    min-width: 200px;
    font-size: 0.85rem;
    color: var(--text-secondary);
    text-align: right;
    padding-right: 16px;
}

.chart-bars {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 3px;
}

.bar {
    height: 20px;
    border-radius: 3px;
    display: flex;
    align-items: center;
    min-width: 2px;
    transition: width 0.5s ease;
    position: relative;
}

.bar span {
    font-size: 0.75rem;
    padding-left: 8px;
    white-space: nowrap;
    color: var(--text-primary);
    font-weight: 500;
}

.bar.p50 { background: var(--p50-color); opacity: 0.9; }
.bar.p95 { background: var(--p95-color); opacity: 0.85; }
.bar.p99 { background: var(--p99-color); opacity: 0.8; }

/* Guardrails overhead */
.overhead-card {
    background: var(--bg-secondary);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 1.5rem;
    margin: 1rem 0;
}

.overhead-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 1rem;
    margin-top: 1rem;
}

.metric-card {
    background: var(--bg-tertiary);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 1rem;
    text-align: center;
}

.metric-value {
    font-size: 2rem;
    font-weight: 700;
    line-height: 1.2;
}

.metric-label {
    font-size: 0.8rem;
    color: var(--text-secondary);
    margin-top: 4px;
}

.metric-value.green { color: var(--accent-green); }
.metric-value.orange { color: var(--accent-orange); }
.metric-value.blue { color: var(--accent-blue); }
.metric-value.purple { color: var(--accent-purple); }

/* LaTeX */
.latex-block {
    background: var(--bg-secondary);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 1rem 1.5rem;
    margin: 1rem 0;
    overflow-x: auto;
}

.latex-block pre {
    font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', Consolas, monospace;
    font-size: 0.8rem;
    color: var(--text-secondary);
    white-space: pre;
    line-height: 1.5;
}

.copy-btn {
    background: var(--bg-tertiary);
    border: 1px solid var(--border);
    color: var(--text-secondary);
    padding: 4px 12px;
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.8rem;
    float: right;
}
.copy-btn:hover { color: var(--text-primary); border-color: var(--accent-blue); }

footer {
    margin-top: 3rem;
    padding-top: 1rem;
    border-top: 1px solid var(--border);
    color: var(--text-secondary);
    font-size: 0.8rem;
    text-align: center;
}
</style>
</head>
<body>
<div class="container">
    <h1>SecureAI Gateway</h1>
    <h1 style="font-size:1.3rem; margin-top:-0.3rem;">Performance Benchmark Report</h1>

    <div class="meta">
HTMLEOF

    # Inject dynamic metadata
    cat >> "$HTML_FILE" << EOF
        <span>Timestamp: <strong>${TIMESTAMP_HUMAN}</strong></span>
        <span>Iterations: <strong>${ITERATIONS}</strong></span>
        <span class="tag">Apple Silicon</span>
        <span class="tag">localhost</span>
    </div>

    <h2>Latency Distribution</h2>

    <div class="chart-container">
        <div class="chart-legend">
            <div class="legend-item"><div class="legend-dot p50"></div> p50 (median)</div>
            <div class="legend-item"><div class="legend-dot p95"></div> p95</div>
            <div class="legend-item"><div class="legend-dot p99"></div> p99</div>
        </div>
        ${chart_bars}
    </div>

    <h2>Detailed Results</h2>
    <table>
        <thead>
            <tr>
                <th>Endpoint</th>
                <th>p50 (ms)</th>
                <th>p95 (ms)</th>
                <th>p99 (ms)</th>
                <th>Avg (ms)</th>
                <th>Min / Max (ms)</th>
                <th>Layer</th>
            </tr>
        </thead>
        <tbody>
            ${table_rows}
        </tbody>
    </table>
EOF

    # Throughput section
    if [[ ${#THROUGHPUT_RESULTS[@]} -gt 0 ]]; then
        cat >> "$HTML_FILE" << EOF
    <h2>Throughput (req/s)</h2>
    <table>
        <thead>
            <tr><th>Endpoint</th><th>C=1</th><th>C=5</th><th>C=10</th></tr>
        </thead>
        <tbody>
            ${throughput_rows}
        </tbody>
    </table>
EOF
    fi

    # Guardrails overhead (placeholder -- filled by actual data or simulated)
    cat >> "$HTML_FILE" << 'EOF'
    <h2>Guardrails Overhead Analysis</h2>
    <div class="overhead-card">
        <p>Comparison of sequential vs parallel guardrail execution on the /api/ask pipeline.</p>
        <div class="overhead-grid">
            <div class="metric-card">
                <div class="metric-value green" id="metric-parallel">--</div>
                <div class="metric-label">Parallel execution (ms)</div>
            </div>
            <div class="metric-card">
                <div class="metric-value orange" id="metric-sequential">--</div>
                <div class="metric-label">Sequential execution (ms)</div>
            </div>
            <div class="metric-card">
                <div class="metric-value blue" id="metric-speedup">--</div>
                <div class="metric-label">Speedup factor</div>
            </div>
            <div class="metric-card">
                <div class="metric-value purple" id="metric-overhead">--</div>
                <div class="metric-label">Total guardrails overhead</div>
            </div>
        </div>
    </div>
EOF

    # LaTeX section
    cat >> "$HTML_FILE" << EOF
    <h2>LaTeX Table (Research Paper)</h2>
    <div class="latex-block">
        <button class="copy-btn" onclick="navigator.clipboard.writeText(document.getElementById('latex-src').textContent)">Copy</button>
        <pre id="latex-src">\\begin{table}[htbp]
    \\centering
    \\caption{SecureAI Gateway Latency Benchmarks (${ITERATIONS} iterations, Apple Silicon)}
    \\label{tab:benchmarks}
    \\begin{tabular}{lrrrrr}
        \\toprule
        \\textbf{Endpoint} & \\textbf{p50 (ms)} & \\textbf{p95 (ms)} & \\textbf{p99 (ms)} & \\textbf{Avg (ms)} & \\textbf{Layer} \\\\
        \\midrule
$(echo -e "$latex_rows")        \\bottomrule
    \\end{tabular}
\\end{table}</pre>
    </div>

    <footer>
        Generated by SecureAI Gateway Benchmark Suite &middot; ${TIMESTAMP_HUMAN}
    </footer>
</div>
</body>
</html>
EOF

    ok "HTML report written: ${HTML_FILE}"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    echo ""
    echo -e "${BOLD}  SecureAI Gateway - Performance Benchmark Suite${NC}"
    echo -e "  ${TIMESTAMP_HUMAN}"
    echo ""

    # Pre-flight checks
    log "Checking service availability..."
    local gw_up=false presidio_up=false nemo_up=false

    check_service "${GATEWAY_BASE}/actuator/health" "Gateway" && gw_up=true
    check_service "${PRESIDIO_BASE}/analyze" "Presidio" && presidio_up=true || \
        check_service "${PRESIDIO_BASE}/" "Presidio" && presidio_up=true
    check_service "${NEMO_BASE}/v1/health" "NeMo Guardrails" && nemo_up=true || \
        check_service "${NEMO_BASE}/" "NeMo Guardrails" && nemo_up=true

    if ! $gw_up; then
        fail "Gateway is not reachable at ${GATEWAY_BASE}. Cannot run benchmarks."
        fail "Start the application first: ./mvnw spring-boot:run"
        exit 1
    fi

    # Auth setup
    setup_auth
    define_endpoints

    echo ""
    run_latency_benchmarks
    run_throughput_benchmarks

    # Output results
    print_ascii_table
    write_csv
    generate_html_report

    echo ""
    ok "Benchmark complete. Files:"
    echo "    CSV:  ${CSV_FILE}"
    echo "    HTML: ${HTML_FILE}"
    echo ""
}

main "$@"
