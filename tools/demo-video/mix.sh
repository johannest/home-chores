#!/bin/bash
# Lays the narration clips (video/voice/*.aiff, timed by video/voice.json) under the recorded
# video and writes video/flashchores-demo.mp4: H.264 + AAC, which phones and TikTok play as is.
# Needs a full ffmpeg (brew install ffmpeg); Playwright's own is video-only.
set -eu
DIR=$(cd "$(dirname "$0")" && pwd)
FFMPEG=$(command -v ffmpeg) || { echo "ffmpeg not found — brew install ffmpeg"; exit 1; }
VIDEO=$(ls -t "$DIR"/video/page@*.webm | head -1)
OUT="$DIR/video/flashchores-demo.mp4"

# One input and one adelay per clip, then a plain sum: the lines never overlap, so there is
# nothing for amix's normalisation to balance — it would only make the voice quieter.
ARGS=$(cd "$DIR" && node -e '
  const lines = require("./video/voice.json");
  const inputs = lines.flatMap((l) => ["-i", l.file]);
  const delays = lines.map((l, i) => `[${i + 1}:a]adelay=${l.at}|${l.at}[a${i}]`);
  const mix = lines.map((_, i) => `[a${i}]`).join("")
    + `amix=inputs=${lines.length}:normalize=0,loudnorm=I=-16:TP=-1.5[voice]`;
  console.log(JSON.stringify({ inputs, filter: [...delays, mix].join(";") }));')
INPUTS=$(node -e 'console.log(JSON.parse(process.argv[1]).inputs.map(a => JSON.stringify(a)).join(" "))' "$ARGS")
FILTER=$(node -e 'console.log(JSON.parse(process.argv[1]).filter)' "$ARGS")

eval "\"$FFMPEG\" -y -loglevel error -i \"$VIDEO\" $INPUTS \
  -filter_complex \"$FILTER\" -map 0:v -map \"[voice]\" \
  -c:v libx264 -preset slow -crf 20 -pix_fmt yuv420p -r 30 \
  -c:a aac -b:a 160k -ar 48000 -movflags +faststart \"$OUT\""
echo "mixed to $OUT"
