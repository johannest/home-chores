"""Speaks the demo's narration with Kokoro, a small open-weight neural voice that runs locally.

record.mjs starts this once and keeps it running: loading the model takes seconds, a line takes
a fraction of one, and the recording is rolling while it waits. One JSON request per stdin line,
{"text": ..., "file": ...}; one JSON reply per stdout line, {"ms": duration}. Clips are cached
by voice, speed and text, so a re-record only synthesises the lines that changed.
"""
import hashlib
import json
import os
import shutil
import sys
import warnings

warnings.filterwarnings("ignore")  # torch and spaCy are chatty; stdout is the protocol

import numpy as np
import soundfile as sf
from kokoro import KPipeline

VOICE = os.environ.get("DEMO_VOICE", "af_heart")
SPEED = float(os.environ.get("DEMO_SPEED", "1.15"))
RATE = 24000  # Kokoro's sample rate
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".voice-cache")

os.makedirs(CACHE, exist_ok=True)
# The voice name's first letter is its language: a = American, b = British English.
pipeline = KPipeline(lang_code=VOICE[0], repo_id="hexgrad/Kokoro-82M")
print(json.dumps({"ready": True}), flush=True)

for request in sys.stdin:
    req = json.loads(request)
    key = hashlib.sha1(f"{VOICE}|{SPEED}|{req['text']}".encode()).hexdigest()[:16]
    cached = os.path.join(CACHE, f"{VOICE}-{key}.wav")
    if not os.path.exists(cached):
        audio = np.concatenate([a for _, _, a in pipeline(req["text"], voice=VOICE, speed=SPEED)])
        sf.write(cached, audio, RATE)
    shutil.copyfile(cached, req["file"])
    print(json.dumps({"ms": round(sf.info(cached).duration * 1000)}), flush=True)
