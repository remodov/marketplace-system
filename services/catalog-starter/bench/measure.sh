#!/usr/bin/env bash
# Замер поиска по каталогу: три прогона, среднее время ответа.
# Использование: bench/measure.sh "клавиатура keychron"
set -euo pipefail

QUERY="${1:-keychron}"
HOST="${HOST:-localhost:8082}"
RUNS="${RUNS:-10}"

total=0
for _ in $(seq "$RUNS"); do
    t=$(curl -s -o /dev/null -w '%{time_total}' "http://$HOST/products?query=$(printf %s "$QUERY" | sed 's/ /%20/g')")
    total=$(echo "$total + $t" | bc -l)
done

printf 'запрос «%s»: среднее %.0f мс за %s прогонов\n' "$QUERY" "$(echo "$total / $RUNS * 1000" | bc -l)" "$RUNS"
