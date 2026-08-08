"""Fetch tech-news items from editorial RSS feeds within a time window.

Every feed here belongs to a publication with an editorial staff and named
authors. Deliberately no user-submitted aggregators (Hacker News, Reddit,
Lobsters): those carry whatever anyone chose to post, which is the opposite
of what this newsletter is for.
"""
import re
from datetime import datetime, timezone

import feedparser

# (Source name, RSS feed URL). Add/remove feeds here to change coverage.
FEEDS = [
    ("TechCrunch", "https://techcrunch.com/feed/"),
    ("The Verge", "https://www.theverge.com/rss/index.xml"),
    ("Ars Technica", "http://feeds.arstechnica.com/arstechnica/index"),
    ("Wired", "https://www.wired.com/feed/rss"),
    ("Engadget", "https://www.engadget.com/rss.xml"),
    ("VentureBeat", "https://venturebeat.com/feed/"),
    ("Electrek", "https://electrek.co/feed/"),
    ("Tom's Hardware", "https://www.tomshardware.com/feeds/all"),
    ("IEEE Spectrum", "https://spectrum.ieee.org/rss/fulltext"),
    ("MIT Technology Review", "https://www.technologyreview.com/feed/"),
]


def _clean_html(raw):
    text = re.sub(r"<[^>]+>", " ", raw or "")
    text = re.sub(r"\s+", " ", text).strip()
    return text


def _entry_time(entry):
    for key in ("published_parsed", "updated_parsed"):
        t = entry.get(key)
        if t:
            return datetime(*t[:6], tzinfo=timezone.utc)
    return None


def fetch_rss_items(window_start, window_end):
    items = []
    for source, url in FEEDS:
        try:
            parsed = feedparser.parse(url, request_headers={"User-Agent": "daily-tech-newsletter/1.0"})
        except Exception as exc:  # pragma: no cover - network failures shouldn't kill the whole run
            print(f"[warn] failed to fetch {source}: {exc}")
            continue
        for entry in parsed.entries:
            published = _entry_time(entry)
            if not published or not (window_start <= published <= window_end):
                continue
            items.append(
                {
                    "source": source,
                    "title": _clean_html(entry.get("title", "")),
                    "summary": _clean_html(entry.get("summary", "") or entry.get("description", "")),
                    "link": entry.get("link", ""),
                    "published": published,
                }
            )
    return items


def fetch_all_items(window_start, window_end):
    items = fetch_rss_items(window_start, window_end)

    seen = set()
    deduped = []
    for item in items:
        key = item["link"] or item["title"].lower()
        if key in seen:
            continue
        seen.add(key)
        deduped.append(item)
    return deduped
