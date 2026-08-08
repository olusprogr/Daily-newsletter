"""Keyword-based categorization for the newsletter (no AI, no API key needed).

Order matters: the first category whose keywords match wins. The list is
sorted narrow-to-broad on purpose. "AI" and "Software" match an enormous
number of tech headlines, so they sit near the end - otherwise a story like
"Nvidia's new AI accelerator" would land under AI and the Chips bucket would
stay empty. Specific beats generic.

Keywords are matched as plain substrings against "title + summary", lowercased.
Watch out for short words that hide inside longer ones: " rust " needs its
spaces or it matches "trust", " fusion" avoids "confusion", " gene " avoids
"general"/"generation". When adding keywords, prefer a leading/trailing space
or a longer phrase over a bare stem - the text is space-padded, so a leading
space still matches a word at the very start of a headline.
"""

CATEGORIES = [
    (
        "Cybersecurity",
        [
            "vulnerability", "exploit", "ransomware", "data breach", "zero-day",
            "zero day", "malware", "phishing", "encryption", "cyberattack",
            "cve-", "backdoor", "botnet", "spyware", "credential", "hacked",
            "security flaw", "patch tuesday",
        ],
    ),
    (
        "Autonomous Driving & EVs",
        [
            "self-driving", "self driving", "autonomous vehicle", "autonomous driving",
            "autonomous car", "robotaxi", "robo-taxi", "waymo", "zoox", "cruise ",
            "tesla", "full self-driving", "driverless", "autopilot",
            "electric vehicle", " ev ", " evs ", "e-bike", "charging station",
        ],
    ),
    (
        "Space & Robotics",
        [
            "spacex", "space launch", "rocket", "satellite", "orbit", "nasa",
            "lunar", "mars mission", "humanoid", "robotics", "robot", "drone",
            "starship", "space station",
        ],
    ),
    (
        "Energy & Climate Tech",
        [
            " fusion", "solar", "nuclear", "energy storage", "power grid",
            " grid ", "heat pump", "emissions", "carbon capture", "renewable",
            "data center power", "geothermal", "hydrogen",
        ],
    ),
    (
        "Science & Biotech",
        [
            "clinical trial", "genome", " gene therapy", "crispr", "vaccine",
            "researchers", "study finds", "biotech", "fda approval", "protein",
            "materials science", "peer-reviewed", "drug discovery",
        ],
    ),
    (
        "Chips & Hardware",
        [
            "chip", "chipset", "processor", "semiconductor", "nvidia", "amd ",
            "intel ", "qualcomm", "tsmc", " gpu", " cpu", "wafer", "lithography",
            "silicon", "graphics card", "motherboard", "smartphone", "laptop",
            "quantum comput", "foldable", " arm ",
        ],
    ),
    (
        "AI & Machine Learning",
        [
            "artificial intelligence", " ai ", "ai-", "ai model", "chatgpt",
            "openai", "anthropic", "claude ", "gpt-", "gpt ", "llm",
            "machine learning", "neural network", "gemini", "copilot",
            "generative", "large language model", "chatbot", "deepmind",
            "midjourney", "stable diffusion",
        ],
    ),
    (
        "Software & Programming",
        [
            "open source", "framework", "compiler", " rust ", "python",
            "javascript", "typescript", "kubernetes", "developer tools",
            "programming language", "code editor", "linux", "database",
            "git commit", "pull request",
        ],
    ),
]


def categorize(item):
    # Pad with spaces so keywords written as " fusion" / " ev " also match at
    # the very start or end of the text, not just mid-sentence.
    text = f" {item['title']} {item['summary']} ".lower()
    for name, keywords in CATEGORIES:
        if any(kw in text for kw in keywords):
            return name
    return None
