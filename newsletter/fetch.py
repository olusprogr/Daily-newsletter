"""Fetch tech-news items from RSS feeds and Hacker News within a time window."""
import re
from datetime import datetime, timezone

import feedparser
import requests

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

HN_API = "https://hn.algolia.com/api/v1/search_by_date"
REQUEST_TIMEOUT = 20


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


def fetch_hn_items(window_start, window_end, min_points=40):
    """Pull well-upvoted Hacker News stories as a global-relevance signal."""
    items = []
    params = {
        "tags": "story",
        "numericFilters": (
            f"created_at_i>{int(window_start.timestamp())},"
            f"created_at_i<{int(window_end.timestamp())},"
            f"points>{min_points}"
        ),
        "hitsPerPage": 50,
    }
    try:
        resp = requests.get(HN_API, params=params, timeout=REQUEST_TIMEOUT)
        resp.raise_for_status()
        for hit in resp.json().get("hits", []):
            url = hit.get("url") or f"https://news.ycombinator.com/item?id={hit.get('objectID')}"
            title = hit.get("title") or ""
            if not title:
                continue
            items.append(
                {
                    "source": "Hacker News",
                    "title": title,
                    "summary": "",
                    "link": url,
                    "published": datetime.fromtimestamp(hit["created_at_i"], tz=timezone.utc),
                }
            )
    except Exception as exc:  # pragma: no cover
        print(f"[warn] failed to fetch Hacker News: {exc}")
    return items


def fetch_all_items(window_start, window_end):
    items = fetch_rss_items(window_start, window_end) + fetch_hn_items(window_start, window_end)

    seen = set()
    deduped = []
    for item in items:
        key = item["link"] or item["title"].lower()
        if key in seen:
            continue
        seen.add(key)
        deduped.append(item)
    return deduped
