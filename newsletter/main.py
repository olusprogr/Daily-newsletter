"""Entry point: fetch -> categorize -> archive -> send to WhatsApp.

Run window: the script computes a rolling 24h window ending "now" (Europe/Berlin),
so it always covers "yesterday 10:00 -> today 10:00" regardless of exactly when
the cron fires. Because GitHub Actions cron runs in UTC and Germany switches
between CET/CEST, the workflow schedules two crons (one per DST state) and this
script only actually proceeds when the *current* Berlin local time is close to
10:00 - the other cron firing that day is a no-op. Use --force to bypass that
gate (e.g. for manual/workflow_dispatch runs or local testing).
"""
import argparse
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from . import build_index
from .categorize import categorize_items
from .fetch import fetch_all_items
from .format_digest import build_markdown_digest, build_whatsapp_messages
from .send_whatsapp import send_messages

BERLIN = ZoneInfo("Europe/Berlin")
TARGET_HOUR = 10
TOLERANCE_MINUTES = 45


def in_run_window(now):
    target = now.replace(hour=TARGET_HOUR, minute=0, second=0, microsecond=0)
    return abs((now - target).total_seconds()) <= TOLERANCE_MINUTES * 60


def run(force=False, dry_run=False):
    now = datetime.now(BERLIN)

    if not force and not in_run_window(now):
        print(
            f"[skip] {now:%H:%M %Z} is outside the {TARGET_HOUR}:00 Europe/Berlin run window "
            "(this is expected for the cron entry that doesn't match the current DST state)."
        )
        return

    window_end = now
    window_start = now - timedelta(hours=24)

    print(f"[info] Fenster: {window_start:%Y-%m-%d %H:%M} - {window_end:%Y-%m-%d %H:%M} (Europe/Berlin)")

    items = fetch_all_items(window_start.astimezone(ZoneInfo("UTC")), window_end.astimezone(ZoneInfo("UTC")))
    print(f"[info] {len(items)} Rohartikel im Zeitfenster gefunden.")

    buckets = categorize_items(items)
    for cat, cat_items in buckets.items():
        print(f"[info]   {cat}: {len(cat_items)} Artikel")

    markdown_digest = build_markdown_digest(buckets, window_start, window_end)
    digest_path = f"digests/{window_end:%Y-%m-%d}.md"
    with open(digest_path, "w", encoding="utf-8") as f:
        f.write(markdown_digest)
    print(f"[info] Digest geschrieben nach {digest_path}")

    build_index.build()

    messages = build_whatsapp_messages(buckets, window_start, window_end)

    if dry_run:
        print("\n\n----- WHATSAPP DRY RUN -----\n")
        for m in messages:
            print(m)
            print("\n---\n")
        return

    send_messages(messages)


def main():
    parser = argparse.ArgumentParser(description="Daily tech newsletter: fetch, archive, send to WhatsApp.")
    parser.add_argument("--force", action="store_true", help="Ignore the 10:00 Europe/Berlin run-window gate.")
    parser.add_argument("--dry-run", action="store_true", help="Print WhatsApp messages instead of sending them.")
    args = parser.parse_args()
    run(force=args.force, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
