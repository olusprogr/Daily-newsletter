# SCHACHT – vertikale Scheibe (v0.1)

Ein Untertage-Idle-Factory-Prototyp. Diese Version ist **eine spielbare
vertikale Scheibe** des Gesamtkonzepts: ein Sektor, das komplette Kern-Loop,
damit man in ~15–30 Minuten merkt, ob der Kern trägt.

## Was drin ist

- **8×8-Gitter**, ein Sektor, portrait, einhändig bedienbar.
- **Adjazenz-Produktion**: Maschinen ziehen Rohstoffe aus *direkt
  angrenzenden* Feldern. Backpressure und Starvation entstehen von selbst –
  Platzierung zählt.
- **Kette**: Bohrer → Roherz → Ofen → Barren → Presse → Platten.
- **Strom** wird pro Sektor bilanziert. Unterversorgung = alle Verbraucher
  laufen anteilig langsamer (kein harter Stopp). Basis-Reaktor + baubare
  Generatoren (verbrennen Roherz).
- **Verschleiß**: Maschinen degradieren unter Last, < 50 % Zustand →
  Output-Malus, 0 % → Stillstand. Manuelle Reparatur oder **Wartungsdrohne**
  (automatisiert, per Tech).
- **Offline-Progress** mit **Zeitleisten-Report**: was lief, was stehen blieb.
  Cap bei 8 h. Verschleiß fällt nur während simulierter Produktionszeit an →
  24 h weg = exakt 8 h, nie schlechter (kein versteckter FOMO).
- **Mini-Tech-Baum** (8 Nodes), bezahlt mit Barren. Kein Prestige, kein Reset.
- Vollständig **offline**, keine Berechtigungen, lokaler Save.

## Steuerung

- Unten die Werkzeug-Palette: **Info** (Maschine antippen → Detail-Panel),
  Maschinen zum Bauen, **Reparieren**, **Abriss**.
- Oben rechts **Tech** und **Statistik**.
- Ein Feld antippen führt das aktive Werkzeug aus.

## Bewusste Vereinfachungen ggü. dem Vollkonzept

- Nur Sektor 1, Tier 1+2 (keine Chemie/Module/Datenkerne, keine Tiefe/Hitze).
- Forschung wird direkt mit Barren bezahlt (statt Forschungspunkte aus Tier 5).
- Kein freies Rohr-/Belt-Routing – reine Adjazenz plus **Lager** als Puffer
  und 1-Feld-Verlängerung.
- Wartungsdrohne verbraucht Strom statt Schmierstoff.
- Programmatisch gezeichnete Tiles, **keine handgezeichnete Pixel-Art**.
- Offline-Sim ist 1-Sekunden-getickt (exakt genug für den Report); eine echte
  Discrete-Event-Simulation wäre der nächste Schritt für lange Zeiträume.

## Selbst bauen

```bash
cd android-game-schacht
gradle assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```
