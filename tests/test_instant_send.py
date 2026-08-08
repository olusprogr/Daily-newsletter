"""End-to-end tests for the 30-minute "send what's new" loop.

These lock in the behaviour that was broken before: a run must never resend an
article, and must never mark articles as sent when the delivery didn't happen.
"""
import os
from datetime import timedelta

import pytest

from newsletter import main, state


def test_first_run_seeds_without_sending(stub, state_file):
    """A fresh checkout must not dump the whole 24h window into WhatsApp."""
    stub.add_article("OpenAI ships a new LLM", "Details.", "https://x.test/1")
    stub.add_article("Waymo expands robotaxi service", "Details.", "https://x.test/2")

    main.run(state_path=state_file)

    assert stub.sent == []
    assert len(state.load(state_file)) == 2


def test_new_article_is_sent_immediately(stub, state_file):
    stub.add_article("OpenAI ships a new LLM", "Details.", "https://x.test/1")
    main.run(state_path=state_file)  # seed

    stub.add_article("Nvidia unveils a new GPU", "Details.", "https://x.test/2")
    main.run(state_path=state_file)

    assert len(stub.sent) == 1
    assert "Nvidia unveils a new GPU" in stub.sent_text
    # The seeded article must not be repeated alongside it.
    assert "OpenAI ships a new LLM" not in stub.sent_text


def test_nothing_new_sends_nothing(stub, state_file):
    stub.add_article("OpenAI ships a new LLM", "Details.", "https://x.test/1")
    main.run(state_path=state_file)
    stub.add_article("Nvidia unveils a new GPU", "Details.", "https://x.test/2")
    main.run(state_path=state_file)
    before = len(stub.sent)

    main.run(state_path=state_file)  # feeds unchanged
    main.run(state_path=state_file)

    assert len(stub.sent) == before


def test_failed_send_is_retried_next_run(stub, state_file):
    """The regression that shipped the newsletter silently: state must only
    advance once WhatsApp actually took the message."""
    stub.add_article("OpenAI ships a new LLM", "Details.", "https://x.test/1")
    main.run(state_path=state_file)

    stub.add_article("Nvidia unveils a new GPU", "Details.", "https://x.test/2")
    stub.whatsapp_status = 500
    with pytest.raises(RuntimeError):
        main.run(state_path=state_file)

    assert "https://x.test/2" not in state.sent_links(state.load(state_file))

    stub.whatsapp_status = 200
    main.run(state_path=state_file)
    assert "Nvidia unveils a new GPU" in stub.sent_text


def test_dry_run_sends_nothing_and_keeps_state(stub, state_file):
    stub.add_article("OpenAI ships a new LLM", "Details.", "https://x.test/1")
    main.run(state_path=state_file)
    before = state.load(state_file)

    stub.add_article("Nvidia unveils a new GPU", "Details.", "https://x.test/2")
    main.run(dry_run=True, state_path=state_file)

    assert stub.sent == []
    assert state.load(state_file) == before


def test_old_and_uncategorised_articles_are_ignored(stub, state_file):
    stub.add_article("OpenAI ships a new LLM", "Details.", "https://x.test/1")
    main.run(state_path=state_file)

    stub.add_article("Ancient GPU review", "Too old.", "https://x.test/old", age=timedelta(days=7))
    stub.add_article("Local bakery wins a prize", "Nothing techy here.", "https://x.test/off-topic")
    main.run(state_path=state_file)

    assert stub.sent == []


def test_burst_is_capped_and_carried_over(stub, state_file, monkeypatch):
    monkeypatch.setattr(main, "MAX_ITEMS_PER_RUN", 2)
    stub.add_article("Seed chip story", "Details.", "https://x.test/seed")
    main.run(state_path=state_file)

    for n in range(5):
        stub.add_article(f"New GPU number {n}", "Details.", f"https://x.test/gpu{n}")
    main.run(state_path=state_file)

    assert state.load(state_file)  # 2 of 5 went out
    sent_now = sum(1 for line in stub.sent_text.splitlines() if line.startswith("• *New GPU"))
    assert sent_now == 2

    main.run(state_path=state_file)
    sent_total = sum(1 for line in stub.sent_text.splitlines() if line.startswith("• *New GPU"))
    assert sent_total == 4  # the rest follows on later runs, nothing is lost


def test_archive_is_written_after_a_successful_send(stub, state_file):
    stub.add_article("Seed story about a chip", "Details.", "https://x.test/seed")
    main.run(state_path=state_file)

    stub.add_article("Nvidia unveils a new GPU", "Details.", "https://x.test/2")
    main.run(state_path=state_file)

    digests = os.listdir("digests")
    assert len(digests) == 1
    archive = open(os.path.join("digests", digests[0]), encoding="utf-8").read()
    assert "Nvidia unveils a new GPU" in archive
    assert os.path.exists(os.path.join("docs", "index.html"))


def test_duplicate_links_across_feeds_are_sent_once(stub, state_file):
    """Both stub feeds serve the same articles; the item must go out once."""
    stub.add_article("Seed story about a chip", "Details.", "https://x.test/seed")
    main.run(state_path=state_file)

    stub.add_article("Nvidia unveils a new GPU", "Details.", "https://x.test/2")
    main.run(state_path=state_file)

    assert stub.sent_text.count("https://x.test/2") == 1
