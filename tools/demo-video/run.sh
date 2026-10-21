#!/bin/bash
# Re-records the demo video against the production jar, on a throwaway database.
#   mvn -Pproduction -DskipTests package   (once, from the repo root)
#   tools/demo-video/run.sh                 -> tools/demo-video/video/*.webm (silent)
#                                              and video/flashchores-demo.mp4 (voiced, via mix.sh)
set -u
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
JAR="$ROOT/target/flashchores-1.0.0.jar"
PORT=8120   # record.mjs's BASE; 8121 keeps the management port off the dev server's

[ -f "$JAR" ] || { echo "No $JAR — build it with: mvn -Pproduction -DskipTests package"; exit 1; }
[ -d "$DIR/node_modules/playwright" ] || (cd "$DIR" && npm install && npx playwright install chromium) || exit 1
# The narration voice: Kokoro in its own venv (Python 3.10–3.12); the model comes from Hugging
# Face on the first line spoken and is cached under ~/.cache/huggingface.
command -v espeak-ng >/dev/null || { echo "Kokoro needs espeak-ng — brew install espeak-ng"; exit 1; }
[ -x "$DIR/.venv/bin/python" ] || (python3.11 -m venv "$DIR/.venv" \
  && "$DIR/.venv/bin/pip" install -q -r "$DIR/requirements.txt") || exit 1

WORK=$(mktemp -d)
JH=$(/usr/libexec/java_home -v 25)
"$JH/bin/java" -Xmx512m -jar "$JAR" \
  --server.port=$PORT --management.server.port=$((PORT + 1)) \
  --spring.datasource.url="jdbc:h2:file:$WORK/demo;DB_CLOSE_DELAY=-1" \
  > "$WORK/app.log" 2>&1 &
APP=$!
trap 'kill $APP 2>/dev/null; wait $APP 2>/dev/null; rm -rf "$WORK"' EXIT

for _ in $(seq 1 60); do grep -q "Started Application" "$WORK/app.log" && break; sleep 1; done
grep -q "Started Application" "$WORK/app.log" || { tail -20 "$WORK/app.log"; exit 1; }

rm -rf "$DIR"/video "$DIR/fail.png"
cd "$DIR" && node record.mjs || exit 1
# The voice-over needs a full ffmpeg; without one the silent .webm is still there.
command -v ffmpeg >/dev/null && "$DIR/mix.sh" || echo "no ffmpeg: skipped the voice-over mix (brew install ffmpeg, then ./mix.sh)"
