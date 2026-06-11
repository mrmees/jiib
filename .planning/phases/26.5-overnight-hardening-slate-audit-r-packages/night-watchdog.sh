#!/usr/bin/env bash
# Phase 26.5 overnight-run watchdog (2026-06-11).
# Relaunches a headless Claude session if the run dies (5h usage window, crash)
# before 26.5-NIGHT-LOG.md says RUN COMPLETE.
#
# Liveness signal = age of the last commit on the night branch (the run commits
# constantly). Stale > STALE_MIN and no recent relaunch attempt => relaunch.
# If the usage window is still exhausted, the relaunch fails fast and we retry
# next cycle. Kill anytime: pkill -f night-watchdog.sh ; rm /tmp/dinghy-night-*.
set -u
REPO=/mnt/e/claude/personal/github/dinghy-display
LOGF=/tmp/dinghy-night-watchdog.log
MARKER=/tmp/dinghy-night-relaunch.marker
NIGHTLOG=$REPO/.planning/phases/26.5-overnight-hardening-slate-audit-r-packages/26.5-NIGHT-LOG.md
BRANCH=gsd/phase-26.5-overnight-hardening
STALE_MIN=90
RETRY_MIN=90
HARD_STOP_EPOCH=$(date -d 'today 09:30' +%s)  # stop watching at 09:30 CT (owner is up)

log() { echo "[$(date '+%F %T')] $*" >> "$LOGF"; }
log "watchdog started (pid $$)"

while true; do
  sleep 900  # 15 min
  [ "$(date +%s)" -gt "$HARD_STOP_EPOCH" ] && { log "hard stop time reached; exiting"; exit 0; }
  grep -q "RUN COMPLETE" "$NIGHTLOG" 2>/dev/null && { log "RUN COMPLETE; exiting"; exit 0; }
  cd "$REPO" || { log "repo missing?"; continue; }
  last=$(git log -1 --format=%ct "$BRANCH" 2>/dev/null || echo 0)
  age_min=$(( ( $(date +%s) - last ) / 60 ))
  if [ "$age_min" -lt "$STALE_MIN" ]; then
    log "healthy (last commit ${age_min}m ago)"; continue
  fi
  if [ -f "$MARKER" ]; then
    m_age=$(( ( $(date +%s) - $(stat -c %Y "$MARKER") ) / 60 ))
    [ "$m_age" -lt "$RETRY_MIN" ] && { log "stale ${age_min}m but relaunch attempted ${m_age}m ago; waiting"; continue; }
  fi
  touch "$MARKER"
  log "STALE ${age_min}m -> relaunching headless claude"
  cd "$REPO" && nohup /home/matt/.local/bin/claude -p \
    "RESUME WATCHDOG RELAUNCH - Phase 26.5 overnight run. Read .planning/phases/26.5-overnight-hardening-slate-audit-r-packages/26.5-NIGHT-LOG.md and resume the run exactly per its 'How to resume' checklist. Branch $BRANCH, never master. If the night log says RUN COMPLETE, do nothing and exit." \
    --dangerously-skip-permissions >> /tmp/dinghy-night-relaunch.log 2>&1 &
  log "relaunch dispatched (pid $!)"
done
