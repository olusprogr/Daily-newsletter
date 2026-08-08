# 📰 Daily Tech Newsletter

Vollautomatischer Tech-Newsletter per WhatsApp. Läuft **alle 30 Minuten** via
GitHub Actions, prüft die Feeds auf neue Artikel und schickt dir jede relevante
Meldung **sofort** — kein Warten auf eine feste Uhrzeit. Was schon draußen ist,
kommt nie ein zweites Mal.

Kein KI-API-Key nötig: Auswahl & Kürzung laufen komplett über RSS-Feeds und
Keyword-Filter (kostenlos, keine Rate-Limits, kein Vendor-Lock-in).

## Themen

- 🔒 **Cybersecurity** — Schwachstellen, Angriffe, Datenlecks, Verschlüsselung
- 🚗 **Autonomous Driving & EVs** — Robotaxis, Fahrassistenz, Elektromobilität
- 🚀 **Space & Robotics** — Raumfahrt, Satelliten, Humanoide, Drohnen
- ⚡ **Energy & Climate Tech** — Fusion, Solar, Netze, Rechenzentrums-Strom
- 🧬 **Science & Biotech** — Forschung, Studien, Genetik, Materialien
- 💻 **Chips & Hardware** — Prozessoren, GPUs, Halbleiterfertigung, Geräte
- 🤖 **AI & Machine Learning** — Modelle, LLMs, Agenten, KI-Regulierung
- ⌨️ **Software & Programming** — Sprachen, Frameworks, Open Source

Pro Beitrag: Titel, max. 2 kurze Sätze Zusammenfassung, Link zum Weiterlesen.
**Die Nachrichten selbst sind auf Englisch**, ebenso das Archiv — nur diese
README ist auf Deutsch.

Die Reihenfolge oben ist die Priorität: der erste Treffer gewinnt. Sie geht
absichtlich von spezifisch nach allgemein, weil „AI" und „Software" auf sehr
viele Schlagzeilen passen. Eine Meldung über einen KI-Beschleuniger landet
deshalb unter *Chips & Hardware* und nicht unter *AI* — sonst wäre der
Chip-Bereich immer leer. Anpassen in `newsletter/categorize.py`.

## Wie „nur Neues" funktioniert

Jeder verschickte Link landet in `state/sent.json`, das der Workflow zurück ins
Repo committet. Der nächste Lauf 30 Minuten später vergleicht die Feeds dagegen
und verschickt nur, was dort noch nicht steht — das Repo ist also selbst der
Speicher, ohne externe Datenbank.

Zwei Eigenschaften, die daran hängen:

- **Der Zustand wird erst nach erfolgreichem Versand geschrieben.** Geht CallMeBot
  nicht ran, bleibt der Artikel „ungesendet" und der nächste Lauf versucht ihn
  erneut, statt ihn stillschweigend zu verlieren.
- **Der allererste Lauf verschickt nichts**, sondern markiert das aktuelle
  24h-Fenster als bekannt — sonst bekämst du beim Start 20 Nachrichten auf
  einmal. Ab dem zweiten Lauf geht nur noch wirklich Neues raus. Mit
  `force: true` sendet auch der erste Lauf.

Maximal 8 Artikel pro Lauf (`MAX_ITEMS_PER_RUN` in `newsletter/main.py`), damit
eine hektische Nachrichtenstunde nicht in CallMeBots Rate-Limit läuft. Der Rest
kommt beim nächsten Lauf.

## Quellen

Ausschließlich **redaktionell betreute Publikationen** — jede mit Redaktion,
Impressum und namentlichen Autoren:

TechCrunch, The Verge, Ars Technica, Wired, Engadget, VentureBeat, Electrek,
Tom's Hardware, IEEE Spectrum, MIT Technology Review, The Register,
BBC Technology, Guardian Technology.

> **Zu Reuters und AP:** beide haben ihre öffentlichen RSS-Feeds abgeschaltet
> (`feeds.reuters.com` ist seit Jahren tot). Ohne bezahlten Agentur-Zugang
> gibt es keinen sauberen Weg dorthin. BBC Technology und Guardian Technology
> stehen deshalb als Ersatz drin: breit, redaktionell kontrolliert, wenig
> Meinung. Wer die Agenturen wirklich braucht, kommt an einer kostenpflichtigen
> Lizenz nicht vorbei — Scraping wäre fragil und rechtlich heikel.

Tote Feeds fallen auf: liefert eine Quelle keine Einträge, schreibt der Lauf
`[warn] <Quelle> returned no entries` ins Log, statt sie still zu übergehen.

> **Bewusst nicht dabei:** nutzergenerierte Aggregatoren wie Hacker News,
> Reddit oder Lobsters. Dort trägt jeder ein, was er möchte — ein Upvote ist
> keine redaktionelle Prüfung, und die verlinkten Ziele sind beliebig
> (Gists, private Repos, Foren-Posts). Wer solche Quellen wieder aufnimmt,
> holt sich genau diese Beliebigkeit zurück.

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
> Deshalb die Deckelung auf 8 Artikel pro Lauf. Bei Bedarf kann
> `newsletter/send_whatsapp.py` später gegen Twilio oder die offizielle
> WhatsApp Cloud API von Meta ausgetauscht werden.

### 2. GitHub Actions aktivieren

Der Workflow `.github/workflows/newsletter.yml` läuft automatisch, sobald er
im **default branch** (`main`) liegt — GitHub führt geplante (`schedule`)
Workflows nur dort aus. Der Cron `*/30 * * * *` prüft alle 30 Minuten auf neue
Artikel. GitHub verzögert geplante Läufe bei Last oft um 5–20 Minuten; das
macht hier nichts, weil nichts mehr an einer festen Uhrzeit hängt.

Der Workflow braucht Schreibrechte (`permissions: contents: write`), weil er
`state/sent.json` nach jedem Versand zurück ins Repo committet.

Manuell testen: **Actions → Tech Newsletter → Run workflow** mit
`dry_run: true` — dann landen die Nachrichten nur im Workflow-Log, ohne
WhatsApp und ohne den Zustand zu verändern.

> 💡 Kosten: ~48 Läufe/Tag à ~1 Minute ≈ 1.400 Actions-Minuten/Monat. Der
> Free-Tier für private Repos liegt bei 2.000 Minuten — passt, ist aber kein
> großer Puffer. Wenn es knapp wird: Cron auf `0 * * * *` (stündlich) stellen.

### 3. Archiv-Webseite (optional, aber empfohlen)

Jeder Versand schreibt den Tagesstand nach `digests/YYYY-MM-DD.md` und baut
`docs/index.html` neu (Archiv aller bisherigen Tage). Um das als Webseite
verfügbar zu machen:

**Settings → Pages → Source: "Deploy from a branch" → Branch: `main`,
Ordner: `/docs`** auswählen und speichern. Danach ist der Newsletter-Verlauf
unter `https://<dein-user>.github.io/<repo-name>/` erreichbar.

## Lokal testen

```bash
pip install -r requirements.txt
python -m newsletter.main --dry-run
```

`--dry-run` sendet nichts an WhatsApp und fasst `state/sent.json` nicht an,
sondern gibt die Nachrichten nur im Terminal aus. `--force` verschickt auch
beim allerersten Lauf, statt den Zustand nur zu initialisieren.

### Tests

```bash
pip install -r requirements-dev.txt
python -m pytest tests/ -v
```

Die Tests fahren die komplette Pipeline gegen einen lokalen HTTP-Stub für
Feeds und CallMeBot — kein Test hängt am echten Netz. Abgedeckt sind unter
anderem: erster Lauf verschickt nichts, ein neuer Artikel geht sofort raus,
ein bereits verschickter nie wieder, und ein fehlgeschlagener Versand wird
beim nächsten Lauf wiederholt statt verloren.

## Wie die Zusammenfassung funktioniert

- Artikel werden nach Erscheinungsdatum auf ein 24h-Fenster gefiltert (großzügig
  gewählt, damit ein verpasster Lauf nichts verschluckt — die Dopplungssperre
  erledigt den Rest).
- Keyword-Matching ordnet jeden Artikel genau einer Kategorie zu
  (Priorität: AI → Autonomes Fahren → Hardware → Innovationen). Artikel, die in
  keine Kategorie fallen, werden verworfen.
- Der Beschreibungstext wird auf die ersten zwei Sätze gekürzt.
- Bereits verschickte Links werden anhand von `state/sent.json` aussortiert.
