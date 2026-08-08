"""Keyword-based categorization for the newsletter (no AI, no API key needed)."""

# Order matters: first matching category wins. Edit these lists to tune what
# counts as "wichtig" for each topic.
CATEGORIES = [
    (
        "AI",
        [
            "artificial intelligence", " ai ", "ai-", "chatgpt", "openai", "anthropic",
            "claude ", "gpt-", "gpt ", "llm", "machine learning", "neural network",
            "gemini", "copilot", "generative ai", "large language model", "chatbot",
            "deepmind", "midjourney", "stable diffusion",
        ],
    ),
    (
        "Autonomes Fahren",
        [
            "self-driving", "self driving", "autonomous vehicle", "autonomous driving",
            "autonomous car", "robotaxi", "robo-taxi", "waymo", "cruise ", "zoox",
            "tesla fsd", "full self-driving", "driverless", "autopilot",
        ],
    ),
    (
        "Hardware",
        [
            "chip", "chipset", "processor", "semiconductor", "nvidia", "amd ", "intel ",
            "qualcomm", " gpu", " cpu", "smartphone", "quantum computer", "quantum chip",
            "foldable phone", "silicon", "graphics card", "motherboard", "wafer",
        ],
    ),
    (
        "Innovationen",
        [
            "robot", "robotics", "spacex", "space launch", "satellite", "battery breakthrough",
            "fusion energy", "biotech", "breakthrough", "startup raises", "innovation",
            "3d printing", "wearable", "humanoid",
        ],
    ),
]


def categorize(item):
    text = f"{item['title']} {item['summary']}".lower()
    for name, keywords in CATEGORIES:
        if any(kw in text for kw in keywords):
            return name
    return None


def categorize_items(items, max_per_category=5):
    """Bucket items by category, newest first, capped per category."""
    buckets = {name: [] for name, _ in CATEGORIES}
    for item in sorted(items, key=lambda i: i["published"], reverse=True):
        cat = categorize(item)
        if cat is None:
            continue
        if len(buckets[cat]) >= max_per_category:
            continue
        buckets[cat].append(item)
    return buckets
