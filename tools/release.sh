#!/usr/bin/env bash
#
# release.sh — deploy or roll back a FlashChores release
#
# Usage:
#   ./release.sh                        # deploy auto-detected flashchores-<version>.jar
#   ./release.sh flashchores-1.0.0.jar  # deploy a specific jar
#   ./release.sh --rollback             # restore flashchores-prev.jar (app only)
#   ./release.sh --rollback --with-db   # also restore the newest DB backup
#   ./release.sh --list                 # show what's available to roll back to
#   ./release.sh --stop                 # just stop the app
#
set -euo pipefail

# ---------------------------------------------------------------- config ----
APP_JAR="flashchores.jar"
PREV_JAR="flashchores-prev.jar"
START_SCRIPT="./flashchores.sh"
DB_FILE="data/homechores.mv.db"
LOG_DIR="logs"
STOP_TIMEOUT=30        # seconds to wait for graceful shutdown
KEEP_DB_BACKUPS=10     # how many timestamped DB backups to keep
HEALTH_URL=""          # e.g. "http://127.0.0.1:8080/" — leave empty to skip

# ----------------------------------------------------------------- setup ----
SELF="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"
cd "$(dirname "$SELF")"

TS="$(date +%Y%m%d-%H%M%S)"
log()  { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die()  { printf '[%s] ERROR: %s\n' "$(date '+%H:%M:%S')" "$*" >&2; exit 1; }

pids_running() { pgrep -u "$(id -u)" -f "java.*${APP_JAR}" 2>/dev/null || true; }

stop_app() {
  local pids
  pids="$(pids_running)"

  if [ -z "$pids" ]; then
    log "No running app found — nothing to stop"
    return 0
  fi

  log "Stopping running app (PID $(echo "$pids" | tr '\n' ' '))"
  kill $pids 2>/dev/null || true

  local i
  for ((i = 0; i < STOP_TIMEOUT; i++)); do
    [ -z "$(pids_running)" ] && break
    sleep 1
  done

  if [ -n "$(pids_running)" ]; then
    log "Still alive after ${STOP_TIMEOUT}s — sending SIGKILL"
    pkill -9 -u "$(id -u)" -f "java.*${APP_JAR}" || true
    sleep 2
  fi
  log "App stopped"
}

start_app() {
  log "Starting app"
  "$START_SCRIPT"
  sleep 5

  if [ -z "$(pids_running)" ]; then
    log "App is not running — check ${LOG_DIR}/"
    return 1
  fi

  if [ -n "$HEALTH_URL" ]; then
    local i
    for ((i = 0; i < 30; i++)); do
      if curl -fsS -o /dev/null --max-time 3 "$HEALTH_URL"; then
        log "Health check OK"
        return 0
      fi
      sleep 2
    done
    log "WARNING: health check did not pass, but the process is alive"
  fi
  return 0
}

backup_db() {
  [ -f "$DB_FILE" ] || { log "WARNING: $DB_FILE not found — first deployment?"; return 0; }

  local bak="data/homechores.mv.${TS}.bak"
  cp -p "$DB_FILE" "$bak" || die "Database backup failed — aborting."
  log "Database backed up to $bak ($(du -h "$bak" | cut -f1))"

  local old
  old="$(db_backups | tail -n +$((KEEP_DB_BACKUPS + 1)) || true)"
  if [ -n "$old" ]; then
    echo "$old" | xargs -r rm -f
    log "Pruned $(echo "$old" | wc -l) old database backup(s)"
  fi
}

# Newest first. Sorted by the timestamp in the file name — NOT by mtime: cp -p keeps the
# source's mtime, so after a --with-db restore mtime order no longer matches backup order.
db_backups()       { ls -1 data/homechores.mv.*.bak 2>/dev/null | sort -r || true; }
newest_db_backup() { db_backups | head -1 || true; }
backup_time()      { local t="${1##*/homechores.mv.}"; t="${t%.bak}"; echo "${t:0:4}-${t:4:2}-${t:6:2} ${t:9:2}:${t:11:2}"; }

jar_info() {
  local f="$1"
  [ -f "$f" ] || { echo "(missing)"; return; }
  echo "$(du -h "$f" | cut -f1), built $(date -r "$f" '+%Y-%m-%d %H:%M')"
}

# ------------------------------------------------------------ parse args ----
MODE="deploy"
WITH_DB=0
NEW_JAR=""

while [ $# -gt 0 ]; do
  case "$1" in
    --rollback) MODE="rollback" ;;
    --with-db)  WITH_DB=1 ;;
    --list)     MODE="list" ;;
    --stop)     MODE="stop" ;;
    -h|--help)  sed -n '2,12p' "$SELF"; exit 0 ;;
    -*)         die "Unknown option: $1" ;;
    *)          NEW_JAR="$1" ;;
  esac
  shift
done

mkdir -p "$LOG_DIR"

# ------------------------------------------------------------- --list -------
if [ "$MODE" = "list" ]; then
  echo
  echo "Current app : $APP_JAR   — $(jar_info "$APP_JAR")"
  echo "Rollback to : $PREV_JAR  — $(jar_info "$PREV_JAR")"
  echo "Running     : $([ -n "$(pids_running)" ] && echo "yes (PID $(pids_running | tr '\n' ' '))" || echo "no")"
  echo
  echo "Database backups (newest first):"
  db_backups | head -"$KEEP_DB_BACKUPS" \
    | while read -r b; do echo "  $b  ($(du -h "$b" | cut -f1), $(backup_time "$b"))"; done
  [ -n "$(newest_db_backup)" ] || echo "  (none)"
  echo
  exit 0
fi

# ------------------------------------------------------------- --stop -------
if [ "$MODE" = "stop" ]; then
  stop_app
  exit 0
fi

# --------------------------------------------------------- --rollback -------
if [ "$MODE" = "rollback" ]; then
  [ -f "$PREV_JAR" ] || die "$PREV_JAR does not exist — nothing to roll back to."

  log "Rolling back to $PREV_JAR ($(jar_info "$PREV_JAR"))"

  DB_RESTORE=""
  if [ "$WITH_DB" = "1" ]; then
    DB_RESTORE="$(newest_db_backup)"
    [ -n "$DB_RESTORE" ] || die "No database backup found to restore."
    echo
    echo "  This will overwrite $DB_FILE with $DB_RESTORE"
    echo "  (taken $(backup_time "$DB_RESTORE")). Any data written since then is lost."
    echo
    read -r -p "  Type 'yes' to continue: " confirm
    [ "$confirm" = "yes" ] || die "Aborted."
  fi

  stop_app

  # Always snapshot current state first, so a rollback is itself reversible.
  backup_db

  if [ -n "$DB_RESTORE" ]; then
    cp -p "$DB_RESTORE" "$DB_FILE" || die "Database restore failed."
    log "Database restored from $DB_RESTORE"
  fi

  # Swap jars, keeping the rolled-back-from version as the new "prev".
  if [ -f "$APP_JAR" ]; then
    mv "$APP_JAR" "${APP_JAR}.rolledback"
  fi
  mv "$PREV_JAR" "$APP_JAR"
  mv "${APP_JAR}.rolledback" "$PREV_JAR" 2>/dev/null || true
  log "Swapped $APP_JAR and $PREV_JAR (re-run --rollback to go back again)"

  start_app || { log "Rollback started but app is down — check ${LOG_DIR}/"; exit 1; }
  log "Rollback complete — app running as PID $(pids_running | tr '\n' ' ')"
  exit 0
fi

# ------------------------------------------------- deploy: find new release --
if [ -z "$NEW_JAR" ]; then
  shopt -s nullglob
  candidates=()
  for f in flashchores-*.jar; do
    [ "$f" = "$PREV_JAR" ] && continue
    candidates+=("$f")
  done
  shopt -u nullglob

  case ${#candidates[@]} in
    0) die "No flashchores-<version>.jar found in $PWD — upload the release first." ;;
    1) NEW_JAR="${candidates[0]}" ;;
    *) die "Several candidates found (${candidates[*]}) — pass the one you want as an argument." ;;
  esac
fi

[ -f "$NEW_JAR" ]              || die "$NEW_JAR does not exist."
# -ef compares the actual files, so ./flashchores.jar and absolute paths are caught too.
[ ! "$NEW_JAR" -ef "$APP_JAR" ]  || die "Refusing to deploy $APP_JAR onto itself."
[ ! "$NEW_JAR" -ef "$PREV_JAR" ] || die "Refusing to deploy $PREV_JAR — use --rollback instead."
[ -x "$START_SCRIPT" ]         || die "$START_SCRIPT missing or not executable."

# Cheap sanity check: is it actually a jar (zip magic bytes "PK")?
if [ "$(head -c 2 "$NEW_JAR")" != "PK" ]; then
  die "$NEW_JAR does not look like a jar file (truncated upload?)."
fi

log "Deploying $NEW_JAR ($(du -h "$NEW_JAR" | cut -f1))"

# ------------------------------------------------------------- deploy -------
stop_app
backup_db

rm -f "$PREV_JAR"
if [ -f "$APP_JAR" ]; then
  mv "$APP_JAR" "$PREV_JAR"
  log "Previous app saved as $PREV_JAR"
fi

chmod +x "$NEW_JAR"
mv "$NEW_JAR" "$APP_JAR"
log "Installed $NEW_JAR as $APP_JAR"

if ! start_app; then
  log "Deployment failed. Roll back with:  ./release.sh --rollback"
  exit 1
fi

log "Release complete — app running as PID $(pids_running | tr '\n' ' ')"
log "If something looks wrong:  ./release.sh --rollback"