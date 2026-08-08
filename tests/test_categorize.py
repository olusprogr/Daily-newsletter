"""Categorisation checks, including the short-keyword traps.

Several keywords are written with surrounding spaces (" fusion", " ev ",
" rust ", " gene therapy") so they don't fire inside longer words. These tests
pin both halves of that: the intended word matches, the lookalike does not.
"""
import pytest

from newsletter.categorize import categorize


def cat(title, summary=""):
    return categorize({"title": title, "summary": summary})


@pytest.mark.parametrize(
    "title,expected",
    [
        ("Critical zero-day exploited in VPN appliances", "Cybersecurity"),
        ("Waymo expands robotaxi service to Berlin", "Autonomous Driving & EVs"),
        ("SpaceX launches 60 more Starlink satellites", "Space & Robotics"),
        ("Fusion startup sustains plasma for 10 minutes", "Energy & Climate Tech"),
        ("CRISPR therapy clears trial for sickle cell", "Science & Biotech"),
        ("Nvidia unveils its next AI accelerator GPU", "Chips & Hardware"),
        ("OpenAI releases a smaller reasoning model", "AI & Machine Learning"),
        ("Rust 1.90 lands with faster compile times", "Software & Programming"),
    ],
)
def test_headline_lands_in_expected_category(title, expected):
    assert cat(title) == expected


@pytest.mark.parametrize(
    "title",
    [
        "There is widespread confusion about the rollout",   # not " fusion"
        "Users must trust the new terms of service",         # not " rust "
        "The general election result surprised pollsters",   # not " gene "
        "Local bakery wins a regional prize",                # nothing techy
    ],
)
def test_lookalike_words_do_not_match(title):
    assert cat(title) is None


def test_keyword_at_start_of_headline_still_matches():
    """The text is space-padded, so " fusion" matches a title starting with it."""
    assert cat("Fusion reactor hits a record") == "Energy & Climate Tech"
    assert cat("Rust becomes the default in the kernel") == "Software & Programming"


def test_specific_category_beats_generic_ai():
    """An AI chip story belongs to Chips, not AI - narrow beats broad."""
    assert cat("AMD's new AI inference chip ships in Q3") == "Chips & Hardware"
    assert cat("Waymo uses a new neural network for planning") == "Autonomous Driving & EVs"
