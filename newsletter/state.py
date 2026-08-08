"""Remember which articles were already sent, so a 30-minute cron doesn't resend them.

The state lives in the repo (state/sent.json) and is committed by the workflow
after a successful send. That makes "already sent" survive across runs without
any external database - the repo itself is the store.

Entries are kept for KEEP_DAYS so the file can't grow forever; that is far
longer than the 24h fetch window, so an article can never fall out of the state
while still being fetchable (which would resend it).
"""
import json
import os
from datetime import datetime, timedelta, timezone

STATE_PATH = os.path.join("state", "sent.json")
KEEP_DAYS = 14


def _parse(ts):
    return datetime.fromisoformat(ts)


def load(path=STATE_PATH):
    """Return the list of previously sent item records (newest first)."""
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        try:
            data = json.load(f)
        except json.JSONDecodeError:
            print(f"[warn] {path} ist beschädigt - starte mit leerem Zustand.")
            return []
    return data.get("items", [])


def sent_links(items):
    return {i["link"] for i in items if i.get("link")}


def prune(items, now=None):
    now = now or datetime.now(timezone.utc)
    cutoff = now - timedelta(days=KEEP_DAYS)
    return [i for i in items if _parse(i["sent_at"]) >= cutoff]


def record(items, new_items, now=None):
    """Prepend freshly sent items to the state list."""
    now = now or datetime.now(timezone.utc)
    records = [
        {
            "link": i["link"],
            "title": i["title"],
            "summary": i["summary"],
            "source": i["source"],
            "category": i["category"],
            "published": i["published"].isoformat(),
            "sent_at": now.isoformat(),
        }
        for i in new_items
    ]
    return prune(records + items, now=now)


def save(items, path=STATE_PATH):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"items": items}, f, ensure_ascii=False, indent=2)
        f.write("\n")


def items_sent_on(items, day):
    """All records whose sent_at falls on the given local date."""
    return [i for i in items if _parse(i["sent_at"]).astimezone(day.tzinfo).date() == day.date()]
