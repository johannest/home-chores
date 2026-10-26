#!/bin/bash
# Re-records one demo video against the production jar, on a throwaway database.
#   mvn -Pproduction -DskipTests package   (once, from the repo root)
#   tools/demo-video/run.sh basics          -> tools/demo-video/video/basics/*.webm (silent)
#                                              and video/flashchores-basics.mp4 (voiced, via mix.sh)
# The name picks the scene set in scenes/<name>.mjs.
set -u
NAME=${1:-basics}
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
JAR="$ROOT/target/flashchores-1.0.0.jar"
# record.mjs's BASE. The management port is the next one up, off the dev server's. DEMO_PORT
# moves both, so two videos can record side by side.
PORT=${DEMO_PORT:-8120}

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

[ -f "$DIR/scenes/$NAME.mjs" ] || { echo "No scenes/$NAME.mjs — the scene sets are: $(ls "$DIR/scenes" | sed 's/\.mjs$//' | tr '\n' ' ')"; exit 1; }
rm -rf "$DIR/video/$NAME"
cd "$DIR" && DEMO_PORT=$PORT node record.mjs "$NAME" || exit 1
# The voice-over needs a full ffmpeg; without one the silent .webm is still there.
command -v ffmpeg >/dev/null && "$DIR/mix.sh" "$NAME" || echo "no ffmpeg: skipped the voice-over mix (brew install ffmpeg, then ./mix.sh $NAME)"
