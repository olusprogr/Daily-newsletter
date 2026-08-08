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

> ⚠️ **Wichtig:** Die WhatsApp-Kontaktnummer von CallMeBot wird von Zeit zu Zeit
> geändert. Hol dir die aktuell gültige Nummer **immer live von der offiziellen
> Seite**: https://www.callmebot.com/blog/free-api-whatsapp-messages/ — schreib
> sie nicht aus einer Anleitung/einem Blogpost ab, der älter als ein paar
> Wochen sein könnte, sonst landest du ggf. bei einer inzwischen privat
> vergebenen Nummer.

1. Öffne obigen Link und notiere dir die dort aktuell angegebene Kontaktnummer.
2. Speichere diese Nummer als Kontakt in WhatsApp (z. B. als "CallMeBot").
3. Schick diesem Kontakt die Nachricht: `I allow callmebot to send me messages`
4. Du bekommst per WhatsApp-Antwort deinen persönlichen `apikey` zugeschickt.
5. Trage in diesem Repo unter **Settings → Secrets and variables → Actions →
   New repository secret** zwei Secrets ein:
   - `CALLMEBOT_PHONE` → deine Nummer im internationalen Format, z. B. `+491701234567`
   - `CALLMEBOT_APIKEY` → der API-Key aus Schritt 4

> ⚠️ Ohne diese beiden Secrets läuft der Workflow zwar **grün durch**, verschickt
> aber nichts. Kontrolle: **Actions → letzter Lauf → Build & send newsletter →
> Abschnitt `env:`**. Dort muss `CALLMEBOT_APIKEY: ***` stehen. Steht die Zeile
> leer (`CALLMEBOT_APIKEY:`), ist das Secret nicht gesetzt — GitHub maskiert
> gesetzte Secrets immer als `***`.

> Hinweis: CallMeBot ist ein kostenloser Community-Dienst mit Rate-Limits.
> Für einen Newsletter 1×/Tag ist das unproblematisch. Bei Bedarf kann
> `newsletter/send_whatsapp.py` später gegen Twilio oder die offizielle
> WhatsApp Cloud API von Meta ausgetauscht werden.

### 2. GitHub Actions aktivieren

Der Workflow `.github/workflows/newsletter.yml` läuft automatisch, sobald er
im **default branch** (`main`) liegt — GitHub führt geplante (`schedule`)
Workflows nur dort aus. Es gibt zwei Cron-Einträge (~08:00 und ~09:00 UTC),
weil Deutschland zwischen CET/CEST wechselt. Statt anhand der Uhrzeit zu
raten, ob "jetzt wirklich 10 Uhr ist" (fehleranfällig bei Zeitumstellungen),
prüft das Skript live gegen das Repo: Existiert für heute schon ein Digest
(`digests/YYYY-MM-DD.md`), war der Newsletter schon verschickt, und der Lauf
überspringt sich. Wer zuerst am Tag läuft, verschickt also — der andere ist
ein No-Op. Das hat einen praktischen Nebeneffekt: Schlägt der erste Lauf mal
fehl (z. B. Netzwerkfehler), sendet der zweite eine Stunde später automatisch
als Retry.

Manuell testen: **Actions → Daily Tech Newsletter → Run workflow** (mit
`dry_run: true` sendet er nur zur Kontrolle ins Workflow-Log, ohne WhatsApp).
`force: true` (Standard beim manuellen Trigger) sendet auch dann, wenn für
heute schon ein Digest existiert.

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

`--force` sendet auch dann, wenn für heute schon ein Digest existiert;
`--dry-run` sendet nichts an WhatsApp, sondern gibt die Nachrichten nur im
Terminal aus.

Ein Digest unter `digests/` wird erst geschrieben, **nachdem** WhatsApp die
Nachrichten angenommen hat. Ein Dry-Run oder ein fehlgeschlagener Versand
hinterlässt also keinen Digest — sonst würde der zweite Cron-Lauf des Tages
fälschlich denken, der Newsletter sei schon raus, und sich überspringen.

## Wie die Zusammenfassung funktioniert

- Artikel werden nach Erscheinungsdatum auf das 24h-Fenster gefiltert.
- Keyword-Matching ordnet jeden Artikel genau einer Kategorie zu
  (Priorität: AI → Autonomes Fahren → Hardware → Innovationen).
- Pro Kategorie werden max. 5 Artikel behalten (neueste zuerst).
- Der Beschreibungstext wird auf die ersten zwei Sätze gekürzt.
