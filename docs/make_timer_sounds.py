#!/usr/bin/env python3
"""Generates the rest timer sounds in app/src/main/res/raw. Pure synthesis, nothing sampled.

Run from the repo root: python3 docs/make_timer_sounds.py
"""
import array
import math
import wave

RATE = 44100


def note(freq, seconds, peak, decay, attack=0.012):
    """A soft bell: a sine with a little of its octave, a rounded attack and a long fade."""
    n = int(RATE * seconds)
    out = []
    for i in range(n):
        t = i / RATE
        rise = math.sin(min(t / attack, 1) * math.pi / 2) ** 2
        fade = math.exp(-t / decay)
        tail = min((n - i) / (RATE * 0.03), 1)  # reaches true silence, so no click at the end
        wave_ = math.sin(2 * math.pi * freq * t) + 0.12 * math.sin(4 * math.pi * freq * t) * math.exp(-t / (decay / 2))
        out.append(peak * rise * fade * tail * wave_ / 1.12)
    return out


def mix(length, *placed):
    out = [0.0] * int(RATE * length)
    for start, samples in placed:
        offset = int(RATE * start)
        for i, s in enumerate(samples):
            if offset + i < len(out):
                out[offset + i] += s
    return out


def save(name, samples):
    with wave.open(f"app/src/main/res/raw/{name}.wav", "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(array.array("h", (round(s * 32767) for s in samples)).tobytes())


# Timer started: one quiet low note.
save("rest_start", note(523.25, 0.45, 0.28, 0.12))

# Timer ended: two rising notes, louder and longer.
save("rest_end", mix(1.2, (0.0, note(659.25, 0.9, 0.55, 0.28)), (0.24, note(987.77, 0.95, 0.55, 0.3))))
