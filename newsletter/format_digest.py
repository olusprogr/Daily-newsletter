"""Build the WhatsApp message(s) for new articles and the daily archive page."""
from .summarize import two_sentences

CATEGORY_EMOJI = {
    "AI": "🤖",
    "Autonomes Fahren": "🚗",
    "Hardware": "💻",
    "Innovationen": "💡",
}


def _bucket_by_category(items):
    """Group items (dicts carrying a 'category' key) in CATEGORY_EMOJI order."""
    buckets = {cat: [] for cat in CATEGORY_EMOJI}
    for item in items:
        buckets.setdefault(item["category"], []).append(item)
    return buckets


def build_instant_messages(new_items, now, max_chars=1400):
    """WhatsApp message(s) for articles that just showed up in the feeds."""
    count = len(new_items)
    header = (
        f"📰 *Tech News – {count} neue{'r' if count == 1 else ''} Artikel*\n"
        f"Stand: {now:%d.%m. %H:%M}\n"
    )

    messages = []
    current = header

    for cat, items in _bucket_by_category(new_items).items():
        if not items:
            continue

        block_parts = [f"{CATEGORY_EMOJI.get(cat, '')} *{cat}*"]
        for item in items:
            summary = two_sentences(item["summary"], item["title"])
            body = f"• *{item['title']}*"
            if summary:
                body += f"\n{summary}"
            body += f"\n{item['link']}"
            block_parts.append(body)
        block = "\n\n".join(block_parts)

        if len(current) + len(block) > max_chars and current.strip() != header.strip():
            messages.append(current.strip())
            current = header + "(Fortsetzung)\n"

        current += "\n" + block + "\n"

    if current.strip():
        messages.append(current.strip())

    return messages


def build_daily_digest_from_records(records, day):
    """Rebuild a day's archive page from what was actually sent that day."""
    lines = [
        f"# 📰 Tech Newsletter – {day:%d.%m.%Y}",
        f"_{len(records)} Artikel im Laufe des Tages verschickt (Europe/Berlin)_",
        "",
    ]
    for cat, items in _bucket_by_category(records).items():
        if not items:
            continue
        lines.append(f"## {CATEGORY_EMOJI.get(cat, '')} {cat}")
        lines.append("")
        for item in sorted(items, key=lambda i: i["sent_at"], reverse=True):
            summary = two_sentences(item["summary"], item["title"])
            lines.append(f"**{item['title']}** _({item['source']})_")
            if summary:
                lines.append(summary)
            lines.append(f"[Weiterlesen]({item['link']})")
            lines.append("")

    if not records:
        lines.append("_Keine relevanten Tech-News an diesem Tag._")

    return "\n".join(lines)
