"""Regenerate docs/index.html from all archived digests (for GitHub Pages)."""
import glob
import os

import markdown

DIGESTS_DIR = "digests"
DOCS_DIR = "docs"

PAGE_TEMPLATE = """<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<title>Daily Tech Newsletter – Archiv</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  :root {{ color-scheme: light dark; }}
  body {{
    font-family: -apple-system, "Segoe UI", Roboto, sans-serif;
    max-width: 720px;
    margin: 2rem auto;
    padding: 0 1rem;
    line-height: 1.55;
  }}
  h1 {{ font-size: 1.4rem; }}
  article {{
    border-bottom: 1px solid rgba(128,128,128,0.35);
    padding-bottom: 1.5rem;
    margin-bottom: 1.5rem;
  }}
  a {{ color: #2563eb; }}
</style>
</head>
<body>
<h1>📰 Daily Tech Newsletter – Archiv</h1>
{content}
</body>
</html>
"""


def build():
    files = sorted(glob.glob(os.path.join(DIGESTS_DIR, "*.md")), reverse=True)
    articles = []
    for path in files:
        with open(path, encoding="utf-8") as f:
            html = markdown.markdown(f.read())
        articles.append(f"<article>{html}</article>")

    os.makedirs(DOCS_DIR, exist_ok=True)
    with open(os.path.join(DOCS_DIR, "index.html"), "w", encoding="utf-8") as f:
        f.write(PAGE_TEMPLATE.format(content="\n".join(articles) or "<p>Noch keine Ausgaben.</p>"))


if __name__ == "__main__":
    build()
