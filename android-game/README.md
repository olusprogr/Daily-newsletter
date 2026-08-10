# Physik Hüpfer

Ein kleines offline Android-Spiel: Ein Ball fällt durch Schwerkraft nach
unten, ein Tipp auf den Bildschirm gibt ihm einen Sprungimpuls. Weiche den
Röhren aus und sammle Punkte. Der Highscore wird lokal auf dem Gerät
gespeichert (keine Internetverbindung nötig, keine Berechtigungen).

## Selbst bauen

```bash
cd android-game
gradle assembleDebug   # oder: ./gradlew assembleDebug, falls Gradle Wrapper vorhanden
```

Die fertige APK liegt danach unter:
`app/build/outputs/apk/debug/app-debug.apk`

Die APK kann direkt auf einem Android-Gerät installiert werden
(ggf. "Installation aus unbekannten Quellen" erlauben).

## Steuerung

- **Tippen**: Springen / Spiel neu starten nach Game Over
