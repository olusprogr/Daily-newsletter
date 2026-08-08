"""Entry point: fetch -> categorize -> archive -> send to WhatsApp.

Run window: the script computes a rolling 24h window ending "now" (Europe/Berlin),
so it always covers "yesterday 10:00 -> today 10:00" regardless of exactly when
the cron fires.

Instead of guessing from the clock whether "now" is close enough to 10:00
(fragile around DST changes), the script checks live against the repo itself:
if today's digest file already exists, a newsletter was already sent today,
so this run is a no-op. That means the two daily cron entries (one per DST
state) never need to fight over which one is "right" - whichever runs first
each day sends the newsletter, and the second one harmlessly finds the digest
already there and skips.

For that marker to be honest it must mean "delivered", not merely "built", so
the digest is written only *after* WhatsApp actually accepted the messages.
A failed send (or a --dry-run) therefore leaves no digest behind, and the
second cron run of the day retries for real.

Use --force to send anyway even if today's digest already exists (e.g. for
manual re-runs/testing).
"""
import argparse
import os
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from . import build_index
from .categorize import categorize_items
from .fetch import fetch_all_items
from .format_digest import build_markdown_digest, build_whatsapp_messages
from .send_whatsapp import send_messages

BERLIN = ZoneInfo("Europe/Berlin")
UTC = ZoneInfo("UTC")


def run(force=False, dry_run=False):
    now = datetime.now(BERLIN)
    window_end = now
    window_start = now - timedelta(hours=24)

    digest_path = f"digests/{window_end:%Y-%m-%d}.md"

    if not force and os.path.exists(digest_path):
        print(
            f"[skip] {digest_path} existiert bereits – heute wurde schon ein Newsletter "
            "verschickt (das ist der zweite der beiden täglichen Cron-Läufe, kein Fehler). "
            "Mit --force trotzdem erneut senden."
        )
        return

    print(f"[info] Fenster: {window_start:%Y-%m-%d %H:%M} - {window_end:%Y-%m-%d %H:%M} (Europe/Berlin)")

    items = fetch_all_items(window_start.astimezone(UTC), window_end.astimezone(UTC))
    print(f"[info] {len(items)} Rohartikel live abgerufen und im Zeitfenster gefunden.")

    buckets = categorize_items(items)
    for cat, cat_items in buckets.items():
        print(f"[info]   {cat}: {len(cat_items)} Artikel")

    messages = build_whatsapp_messages(buckets, window_start, window_end)

    if dry_run:
        print("\n\n----- WHATSAPP DRY RUN -----\n")
        for m in messages:
            print(m)
            print("\n---\n")
        print(
            f"[dry-run] {digest_path} wurde bewusst NICHT geschrieben - sonst würde der "
            "echte Lauf später am Tag denken, der Newsletter sei schon raus, und sich "
            "überspringen."
        )
        return

    sent = send_messages(messages)
    if sent == 0:
        raise RuntimeError(
            "Keine einzige WhatsApp-Nachricht konnte zugestellt werden - der Digest wird "
            "deshalb nicht archiviert, damit der zweite Cron-Lauf heute automatisch erneut "
            "versucht."
        )
    print(f"[info] {sent}/{len(messages)} WhatsApp-Nachrichten zugestellt.")

    markdown_digest = build_markdown_digest(buckets, window_start, window_end)
    with open(digest_path, "w", encoding="utf-8") as f:
        f.write(markdown_digest)
    print(f"[info] Digest geschrieben nach {digest_path}")

    build_index.build()


def main():
    parser = argparse.ArgumentParser(description="Daily tech newsletter: fetch, archive, send to WhatsApp.")
    parser.add_argument(
        "--force",
        action="store_true",
        help="Trotzdem senden, auch wenn für heute schon ein Digest existiert.",
    )
    parser.add_argument("--dry-run", action="store_true", help="Print WhatsApp messages instead of sending them.")
    args = parser.parse_args()
    run(force=args.force, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
