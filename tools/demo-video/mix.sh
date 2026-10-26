#!/bin/bash
# Lays one video's narration clips (video/<name>/voice/*.wav, timed by video/<name>/voice.json)
# under its recording and writes video/flashchores-<name>.mp4: H.264 + AAC, which phones and
# TikTok play as is. The recording starts with the page; voice.json's startMs says where the
# video does, and what the scene set up before that is cut off. Needs a full ffmpeg
# (brew install ffmpeg); Playwright's own is video-only.
set -eu
DIR=$(cd "$(dirname "$0")" && pwd)
NAME=${1:-basics}
FFMPEG=$(command -v ffmpeg) || { echo "ffmpeg not found — brew install ffmpeg"; exit 1; }
VIDEO=$(ls -t "$DIR/video/$NAME"/page@*.webm | head -1)
OUT="$DIR/video/flashchores-$NAME.mp4"

# One input and one adelay per clip, then a plain sum: the lines never overlap, so there is
# nothing for amix's normalisation to balance — it would only make the voice quieter.
ARGS=$(cd "$DIR" && node -e '
  const { startMs, lines } = require(`./video/${process.argv[1]}/voice.json`);
  const inputs = lines.flatMap((l) => ["-i", l.file]);
  const delays = lines.map((l, i) => `[${i + 1}:a]adelay=${l.at}|${l.at}[a${i}]`);
  const mix = lines.map((_, i) => `[a${i}]`).join("")
    + `amix=inputs=${lines.length}:normalize=0,loudnorm=I=-16:TP=-1.5[voice]`;
  console.log(JSON.stringify({ start: (startMs / 1000).toFixed(3), inputs, filter: [...delays, mix].join(";") }));' "$NAME")
START=$(node -e 'console.log(JSON.parse(process.argv[1]).start)' "$ARGS")
INPUTS=$(node -e 'console.log(JSON.parse(process.argv[1]).inputs.map(a => JSON.stringify(a)).join(" "))' "$ARGS")
FILTER=$(node -e 'console.log(JSON.parse(process.argv[1]).filter)' "$ARGS")

# -ss before the recording decodes from the last keyframe and drops frames up to the cut, so the
# cut is exact; the lines' times are already relative to it.
eval "\"$FFMPEG\" -y -loglevel error -ss $START -i \"$VIDEO\" $INPUTS \
  -filter_complex \"$FILTER\" -map 0:v -map \"[voice]\" \
  -c:v libx264 -preset slow -crf 20 -pix_fmt yuv420p -r 30 \
  -c:a aac -b:a 160k -ar 48000 -movflags +faststart \"$OUT\""
echo "mixed to $OUT"
