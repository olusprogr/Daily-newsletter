"""Trim item text down to max. 2 short sentences, no AI involved."""
import re

MAX_LEN = 300


def two_sentences(text, fallback_title):
    text = (text or "").strip()
    if not text:
        return fallback_title.strip()

    sentences = re.split(r"(?<=[.!?])\s+", text)
    sentences = [s.strip() for s in sentences if s.strip()]
    short = " ".join(sentences[:2]).strip()

    if not short:
        short = fallback_title.strip()

    if len(short) > MAX_LEN:
        short = short[: MAX_LEN - 1].rstrip() + "…"

    return short
