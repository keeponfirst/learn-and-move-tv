#!/usr/bin/env bash
# 發布前檢查：repo 裡不該出現內部工作代號、內部路徑或個人資訊。
# 有任何一筆就列出來並以非 0 結束。檔名也一起檢查。
#
# 用法：bash scripts/check-clean.sh
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SELF="scripts/check-clean.sh"

# 區分大小寫的樣式
CASE_PATTERNS=(
  'P[1-4]-[0-9]+'
  '(^|[^A-Za-z0-9_])P[1-4]([^A-Za-z0-9_-]|$)'
  '本席|總舵|工頭|筆桿|探子|小櫃|席位|派工|主管'
  '女兒'
)
# 不分大小寫的樣式
NOCASE_PATTERNS=(
  'henry'
  'studio/|p4-content|outbox|kof-'
)

EXCLUDES=(--exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.kotlin
          --exclude-dir=.idea --exclude=check-clean.sh --exclude=local.properties)

hits=0
scan() {
  local flags=$1 pattern=$2 out names
  out=$(cd "$ROOT" && grep -rnIE $flags "${EXCLUDES[@]}" -e "$pattern" . 2>/dev/null)
  if [ -n "$out" ]; then
    echo "$out"
    hits=$((hits + $(printf '%s\n' "$out" | wc -l)))
  fi
  names=$(cd "$ROOT" && find . \( -name .git -o -name build -o -name .gradle -o -name .kotlin -o -name .idea \) -prune \
    -o -print | grep -E $flags -e "$pattern" | grep -v "^./${SELF}$")
  if [ -n "$names" ]; then
    printf '檔名：%s\n' $names
    hits=$((hits + $(printf '%s\n' "$names" | wc -l)))
  fi
}

for p in "${CASE_PATTERNS[@]}"; do scan "" "$p"; done
for p in "${NOCASE_PATTERNS[@]}"; do scan "-i" "$p"; done

echo "check-clean：${hits} 筆"
[ "$hits" -eq 0 ]
