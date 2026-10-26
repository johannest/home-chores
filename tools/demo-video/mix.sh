#!/bin/bash
# Splices one video's cut list (video/<name>/cuts.json: which screen's recording, from when to
# when) in script order, lays the narration clips (video/<name>/voice/*.wav, timed by
# video/<name>/voice.json) under it and writes video/flashchores-<name>.mp4: H.264 + AAC, which
# phones and TikTok play as is. Needs a full ffmpeg (brew install ffmpeg); Playwright's own is
# video-only.
set -eu
DIR=$(cd "$(dirname "$0")" && pwd)
NAME=${1:-basics}
FFMPEG=$(command -v ffmpeg) || { echo "ffmpeg not found — brew install ffmpeg"; exit 1; }
OUT="$DIR/video/flashchores-$NAME.mp4"

# Every recording is an input once; each cut trims its recording and the cuts concatenate,
# so a video that never leaves one screen is that recording start to end. One input and one
# adelay per clip, then a plain sum: the lines never overlap, so there is nothing for amix's
# normalisation to balance — it would only make the voice quieter.
ARGS=$(cd "$DIR" && node -e '
  const cuts = require(`./video/${process.argv[1]}/cuts.json`);
  const lines = require(`./video/${process.argv[1]}/voice.json`);
  const files = [...new Set(cuts.map((c) => c.file))];
  const trims = cuts.map((c, i) =>
    `[${files.indexOf(c.file)}:v]trim=start=${c.from / 1000}:end=${c.to / 1000},setpts=PTS-STARTPTS[c${i}]`);
  const splice = cuts.map((_, i) => `[c${i}]`).join("") + `concat=n=${cuts.length}:v=1:a=0[vid]`;
  const delays = lines.map((l, i) => `[${files.length + i}:a]adelay=${l.at}|${l.at}[a${i}]`);
  const mix = lines.map((_, i) => `[a${i}]`).join("")
    + `amix=inputs=${lines.length}:normalize=0,loudnorm=I=-16:TP=-1.5[voice]`;
  const inputs = [...files, ...lines.map((l) => l.file)].flatMap((f) => ["-i", f]);
  console.log(JSON.stringify({ inputs, filter: [...trims, splice, ...delays, mix].join(";") }));' "$NAME")
INPUTS=$(node -e 'console.log(JSON.parse(process.argv[1]).inputs.map(a => JSON.stringify(a)).join(" "))' "$ARGS")
FILTER=$(node -e 'console.log(JSON.parse(process.argv[1]).filter)' "$ARGS")

eval "\"$FFMPEG\" -y -loglevel error $INPUTS \
  -filter_complex \"$FILTER\" -map \"[vid]\" -map \"[voice]\" \
  -c:v libx264 -preset slow -crf 20 -pix_fmt yuv420p -r 30 \
  -c:a aac -b:a 160k -ar 48000 -movflags +faststart \"$OUT\""
echo "mixed to $OUT"
