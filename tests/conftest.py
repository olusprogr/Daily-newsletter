"""A local stand-in for the RSS feeds and CallMeBot.

The tests drive the real pipeline end to end; only the two network endpoints
are swapped for a loopback HTTP server, so no test ever depends on TechCrunch
being up (or on the sandbox being allowed to reach it).
"""
import email.utils
import json
import threading
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

import pytest

from newsletter import fetch, send_whatsapp


class Stub:
    """Serves the feeds, Hacker News and CallMeBot for one test."""

    def __init__(self):
        self.articles = []
        self.sent = []
        self.whatsapp_status = 200

    def add_article(self, title, summary, link, age=timedelta(hours=2)):
        self.articles.append(
            {
                "title": title,
                "summary": summary,
                "link": link,
                "published": datetime.now(timezone.utc) - age,
            }
        )

    def feed_xml(self):
        items = "\n".join(
            f"<item><title>{a['title']}</title>"
            f"<description>{a['summary']}</description>"
            f"<link>{a['link']}</link>"
            f"<pubDate>{email.utils.format_datetime(a['published'])}</pubDate></item>"
            for a in self.articles
        )
        return f"<?xml version='1.0' encoding='UTF-8'?><rss version='2.0'><channel>{items}</channel></rss>"

    @property
    def sent_text(self):
        return "\n".join(q["text"][0] for q in self.sent)


@pytest.fixture
def stub(monkeypatch, tmp_path):
    state = Stub()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_GET(self):
            parsed = urlparse(self.path)
            if parsed.path == "/rss":
                body, ctype = state.feed_xml().encode(), "application/rss+xml"
                code = 200
            elif parsed.path == "/hn":
                body, ctype = json.dumps({"hits": []}).encode(), "application/json"
                code = 200
            elif parsed.path == "/whatsapp":
                state.sent.append(parse_qs(parsed.query))
                code = state.whatsapp_status
                body, ctype = (b"Message queued" if code == 200 else b"Rate limited"), "text/plain"
            else:
                code, body, ctype = 404, b"", "text/plain"
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    server = HTTPServer(("127.0.0.1", 0), Handler)
    port = server.server_address[1]
    threading.Thread(target=server.serve_forever, daemon=True).start()

    # Two feeds carrying identical articles - that also exercises the dedup.
    monkeypatch.setattr(
        fetch, "FEEDS", [("Feed-A", f"http://127.0.0.1:{port}/rss"), ("Feed-B", f"http://127.0.0.1:{port}/rss")]
    )
    monkeypatch.setattr(fetch, "HN_API", f"http://127.0.0.1:{port}/hn")
    monkeypatch.setattr(send_whatsapp, "CALLMEBOT_URL", f"http://127.0.0.1:{port}/whatsapp")
    monkeypatch.setattr(send_whatsapp.time, "sleep", lambda *_: None)  # no 8s waits in tests
    monkeypatch.setenv("CALLMEBOT_PHONE", "+491700000000")
    monkeypatch.setenv("CALLMEBOT_APIKEY", "test-key")
    monkeypatch.chdir(tmp_path)  # keep digests/ and docs/ out of the repo

    yield state

    server.shutdown()


@pytest.fixture
def state_file(tmp_path):
    return str(tmp_path / "state" / "sent.json")
