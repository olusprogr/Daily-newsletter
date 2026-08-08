"""Trim item text down to max. 2 short sentences, no AI involved."""
import re

MAX_LEN = 300


def _normalize(s):
    return re.sub(r"[.!?…\s]+$", "", s.strip().lower())


def _truncate_at_word_boundary(text, max_len):
    if len(text) <= max_len:
        return text
    truncated = text[:max_len]
    last_space = truncated.rfind(" ")
    # Only cut back to the last space if that doesn't throw away too much.
    if last_space > max_len * 0.6:
        truncated = truncated[:last_space]
    return truncated.rstrip(",.;:- ") + "…"


def two_sentences(text, fallback_title):
    """Return a max-2-sentence summary, or None if there's nothing to add
    beyond the title (e.g. the feed has no real description)."""
    text = (text or "").strip()
    title = (fallback_title or "").strip()

    if not text or _normalize(text) == _normalize(title):
        return None

    sentences = re.split(r"(?<=[.!?])\s+", text)
    sentences = [s.strip() for s in sentences if s.strip()]
    short = " ".join(sentences[:2]).strip()

    if not short or _normalize(short) == _normalize(title):
        return None

    return _truncate_at_word_boundary(short, MAX_LEN)
