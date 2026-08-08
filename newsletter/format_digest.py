"""Build the WhatsApp message(s) and the Markdown archive entry for a digest run."""
from .summarize import two_sentences

CATEGORY_EMOJI = {
    "AI": "🤖",
    "Autonomes Fahren": "🚗",
    "Hardware": "💻",
    "Innovationen": "💡",
}


def build_markdown_digest(buckets, window_start, window_end):
    lines = [
        f"# 📰 Tech Newsletter – {window_end:%d.%m.%Y}",
        f"_Zeitraum: {window_start:%d.%m. %H:%M} – {window_end:%d.%m. %H:%M} (Europe/Berlin)_",
        "",
    ]
    total = 0
    for cat, items in buckets.items():
        if not items:
            continue
        lines.append(f"## {CATEGORY_EMOJI.get(cat, '')} {cat}")
        lines.append("")
        for item in items:
            summary = two_sentences(item["summary"], item["title"])
            lines.append(f"**{item['title']}** _({item['source']})_")
            lines.append(summary)
            lines.append(f"[Weiterlesen]({item['link']})")
            lines.append("")
            total += 1

    if total == 0:
        lines.append("_Keine relevanten Tech-News in diesem Zeitraum gefunden._")

    return "\n".join(lines)


def build_whatsapp_messages(buckets, window_start, window_end, max_chars=1400):
    header = (
        f"📰 *Tech Newsletter – {window_end:%d.%m.%Y}*\n"
        f"Zeitraum: {window_start:%d.%m %H:%M} – {window_end:%d.%m %H:%M}\n"
    )

    total_items = sum(len(v) for v in buckets.values())
    if total_items == 0:
        return [header + "\nKeine relevanten Tech-News in den letzten 24h gefunden."]

    messages = []
    current = header

    for cat, items in buckets.items():
        if not items:
            continue

        block_parts = [f"{CATEGORY_EMOJI.get(cat, '')} *{cat}*"]
        for item in items:
            summary = two_sentences(item["summary"], item["title"])
            block_parts.append(f"• *{item['title']}*\n{summary}\n{item['link']}")
        block = "\n\n".join(block_parts)

        if len(current) + len(block) > max_chars and current.strip() != header.strip():
            messages.append(current.strip())
            current = header + "(Fortsetzung)\n"

        current += "\n" + block + "\n"

    if current.strip():
        messages.append(current.strip())

    return messages
