"""Entry point: fetch -> categorize -> send what's new -> archive.

The workflow runs every 30 minutes and delivers articles as they show up,
instead of collecting them for one daily digest. What keeps that from
resending the same article every half hour is state/sent.json: every link
that went out is recorded there and committed back to the repo, so the next
run can tell "new" from "already seen" (see newsletter/state.py).

The state is only written *after* WhatsApp accepted the messages. A failed
send therefore leaves no trace, and the next run 30 minutes later retries
those same articles rather than dropping them.

The daily file under digests/ is no longer the trigger for anything - it is
rebuilt from the state after each send, purely as the archive that feeds the
GitHub Pages site.

First run: with no state file yet, everything in the 24h window would look
"new" and flood you with messages. So a first run seeds the state silently
and sends nothing. Use --force to send anyway.
"""
import argparse
import os
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from . import build_index, state
from .categorize import categorize
from .fetch import fetch_all_items
from .format_digest import build_daily_digest_from_records, build_instant_messages
from .send_whatsapp import send_messages

BERLIN = ZoneInfo("Europe/Berlin")
UTC = ZoneInfo("UTC")

# Cap per run so a feed hiccup (or a very busy news hour) can't fire off a
# dozen WhatsApp messages at once and trip CallMeBot's rate limit.
MAX_ITEMS_PER_RUN = 8


def run(force=False, dry_run=False, state_path=state.STATE_PATH):
    now = datetime.now(BERLIN)
    window_start = now - timedelta(hours=24)

    known = state.load(state_path)
    first_run = not os.path.exists(state_path)

    items = fetch_all_items(window_start.astimezone(UTC), now.astimezone(UTC))
    print(f"[info] {len(items)} Artikel im 24h-Fenster abgerufen.")

    categorized = []
    for item in items:
        cat = categorize(item)
        if cat:
            categorized.append({**item, "category": cat})
    print(f"[info] {len(categorized)} davon fallen in eine der Kategorien.")

    already = state.sent_links(known)
    new_items = [i for i in categorized if i["link"] not in already]
    new_items.sort(key=lambda i: i["published"], reverse=True)
    print(f"[info] {len(new_items)} davon wurden noch nie verschickt.")

    if first_run and not force:
        seeded = state.record(known, new_items)
        state.save(seeded, state_path)
        print(
            f"[seed] Erster Lauf: {len(new_items)} Artikel als 'bekannt' markiert, "
            "ohne sie zu verschicken (sonst käme das ganze 24h-Fenster auf einmal). "
            "Ab dem nächsten Lauf geht nur noch wirklich Neues raus."
        )
        return

    if not new_items:
        print("[info] Nichts Neues seit dem letzten Lauf - kein Versand.")
        return

    if len(new_items) > MAX_ITEMS_PER_RUN:
        print(
            f"[info] Auf {MAX_ITEMS_PER_RUN} Artikel begrenzt; der Rest kommt beim "
            "nächsten Lauf in 30 Minuten."
        )
        new_items = new_items[:MAX_ITEMS_PER_RUN]

    messages = build_instant_messages(new_items, now)

    if dry_run:
        print("\n\n----- WHATSAPP DRY RUN -----\n")
        for m in messages:
            print(m)
            print("\n---\n")
        print(
            "[dry-run] state/sent.json wurde bewusst NICHT aktualisiert - sonst würde "
            "der nächste echte Lauf diese Artikel für schon verschickt halten."
        )
        return

    sent = send_messages(messages)
    if sent == 0:
        raise RuntimeError(
            "Keine einzige WhatsApp-Nachricht konnte zugestellt werden - der Zustand "
            "wird deshalb nicht fortgeschrieben, damit der nächste Lauf dieselben "
            "Artikel erneut versucht."
        )
    print(f"[info] {sent}/{len(messages)} WhatsApp-Nachrichten zugestellt.")

    known = state.record(known, new_items)
    state.save(known, state_path)

    os.makedirs("digests", exist_ok=True)
    digest_path = f"digests/{now:%Y-%m-%d}.md"
    with open(digest_path, "w", encoding="utf-8") as f:
        f.write(build_daily_digest_from_records(state.items_sent_on(known, now), now))
    print(f"[info] Archiv aktualisiert: {digest_path}")

    build_index.build()


def main():
    parser = argparse.ArgumentParser(description="Tech newsletter: send new articles as they appear.")
    parser.add_argument(
        "--force",
        action="store_true",
        help="Auch beim allerersten Lauf senden, statt den Zustand nur zu initialisieren.",
    )
    parser.add_argument("--dry-run", action="store_true", help="Print WhatsApp messages instead of sending them.")
    args = parser.parse_args()
    run(force=args.force, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
