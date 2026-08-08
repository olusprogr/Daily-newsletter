# 📰 Daily Tech Newsletter

Vollautomatischer täglicher Tech-Newsletter per WhatsApp. Läuft jeden Tag um
**10:00 Uhr (Europe/Berlin)** via GitHub Actions, sammelt alle relevanten
Tech-News der letzten 24 Stunden (gestern 10 Uhr → heute 10 Uhr), filtert sie
nach Themen und schickt dir eine kompakte Zusammenfassung per WhatsApp.

Kein KI-API-Key nötig: Auswahl & Kürzung laufen komplett über RSS-Feeds und
Keyword-Filter (kostenlos, keine Rate-Limits, kein Vendor-Lock-in).

## Themen

- 🤖 **AI**
- 🚗 **Autonomes Fahren**
- 💻 **Hardware**
- 💡 **Innovationen** (Robotik, Space, Energie, Biotech, ...)

Pro Beitrag: Titel, max. 2 kurze Sätze Zusammenfassung, Link zum Weiterlesen.

## Quellen

RSS: TechCrunch, The Verge, Ars Technica, Wired, Engadget, VentureBeat,
Electrek, Tom's Hardware, IEEE Spectrum, MIT Technology Review — plus stark
upvotete Hacker-News-Storys als globales Relevanzsignal.

Anpassen: `newsletter/fetch.py` (Feeds), `newsletter/categorize.py`
(Keywords je Kategorie).

## Setup (einmalig)

### 1. WhatsApp-Versand über CallMeBot einrichten

CallMeBot ist ein kostenloser Dienst für private WhatsApp-Nachrichten per API.

1. Speichere `+34 644 84 71 66` als Kontakt in WhatsApp (z. B. als "CallMeBot").
2. Schick diesem Kontakt die Nachricht: `I allow callmebot to send me messages`
3. Du bekommst per WhatsApp-Antwort deinen persönlichen `apikey` zugeschickt.
4. Trage in diesem Repo unter **Settings → Secrets and variables → Actions →
   New repository secret** zwei Secrets ein:
   - `CALLMEBOT_PHONE` → deine Nummer im internationalen Format, z. B. `+491701234567`
   - `CALLMEBOT_APIKEY` → der API-Key aus Schritt 3

> Hinweis: CallMeBot ist ein kostenloser Community-Dienst mit Rate-Limits.
> Für einen Newsletter 1×/Tag ist das unproblematisch. Bei Bedarf kann
> `newsletter/send_whatsapp.py` später gegen Twilio oder die offizielle
> WhatsApp Cloud API von Meta ausgetauscht werden.

### 2. GitHub Actions aktivieren

Der Workflow `.github/workflows/newsletter.yml` läuft automatisch, sobald er
im **default branch** (`main`) liegt — GitHub führt geplante (`schedule`)
Workflows nur dort aus. Es gibt zwei Cron-Einträge (08:00 und 09:00 UTC), weil
Deutschland zwischen CET/CEST wechselt; das Skript selbst prüft die aktuelle
Berliner Uhrzeit und sendet nur, wenn es wirklich ~10 Uhr ist — der jeweils
"falsche" Cron-Lauf überspringt sich also automatisch selbst.

Manuell testen: **Actions → Daily Tech Newsletter → Run workflow** (mit
`dry_run: true` sendet er nur zur Kontrolle ins Workflow-Log, ohne WhatsApp).

### 3. Archiv-Webseite (optional, aber empfohlen)

Jeder Lauf schreibt zusätzlich einen Markdown-Digest nach `digests/` und baut
`docs/index.html` neu (Archiv aller bisherigen Ausgaben). Um das als Webseite
verfügbar zu machen:

**Settings → Pages → Source: "Deploy from a branch" → Branch: `main`,
Ordner: `/docs`** auswählen und speichern. Danach ist der Newsletter-Verlauf
unter `https://<dein-user>.github.io/<repo-name>/` erreichbar.

## Lokal testen

```bash
pip install -r requirements.txt
python -m newsletter.main --force --dry-run
```

`--force` überspringt das 10-Uhr-Zeitfenster, `--dry-run` sendet nichts an
WhatsApp, sondern gibt die Nachrichten nur im Terminal aus.

## Wie die Zusammenfassung funktioniert

- Artikel werden nach Erscheinungsdatum auf das 24h-Fenster gefiltert.
- Keyword-Matching ordnet jeden Artikel genau einer Kategorie zu
  (Priorität: AI → Autonomes Fahren → Hardware → Innovationen).
- Pro Kategorie werden max. 5 Artikel behalten (neueste zuerst).
- Der Beschreibungstext wird auf die ersten zwei Sätze gekürzt.
