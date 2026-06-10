# Hytale Mod Template

Skelett fuer neue Hytale Server-Mods.

## Schnellstart

### 1. Template kopieren

```bash
cp -r ~/hytale_mods/template ~/hytale_mods/active/meine_mod
cd ~/hytale_mods/active/meine_mod
```

### 2. Anpassen

**`settings.gradle.kts`** - Mod-Name (= JAR-Name):
```kotlin
rootProject.name = "MeineMod"
```

**`build.gradle.kts`** - Description und Dependencies:
```kotlin
description = "Was die Mod macht"
```

**`manifest.json`** - Name, Main-Klasse, Dependencies:
```json
{
    "Name": "MeineMod",
    "Main": "de.kurashi.meinemod.MeineMod",
    ...
}
```

### 3. Java-Package umbenennen

```bash
mkdir -p src/main/java/de/kurashi/meinemod/events src/main/java/de/kurashi/meinemod/commands
mv src/main/java/de/kurashi/template/TemplateMod.java \
   src/main/java/de/kurashi/meinemod/MeineMod.java
mv src/main/java/de/kurashi/template/events/TemplateEventHandler.java \
   src/main/java/de/kurashi/meinemod/events/MeineEventHandler.java
mv src/main/java/de/kurashi/template/commands/ExampleCommand.java \
   src/main/java/de/kurashi/meinemod/commands/MeineCommand.java
rm -rf src/main/java/de/kurashi/template
```

Package-Deklaration, Import-Pfade und Klassennamen in allen drei Java-Dateien anpassen. Die `requirePermission("template.use")`-Zeile in der Command-Klasse entsprechend anpassen.

### 4. Bauen + Deployen

```bash
chmod +x gradlew
./gradlew build
# JAR liegt in build/libs/MeineMod-1.0.0.jar

# Oder mit deploy-Tool:
deploy meinemod
```

## UI bauen (XAML-first)

Neue UIs starten als XAML, nie als handgeschriebene `.ui`. Seed liegt in
`src/main/xaml/ExamplePage.xaml` (bewusst NICHT in `resources/`, sonst landet
die XAML im JAR):

```bash
xaml2ui src/main/xaml/ExamplePage.xaml \
    -o src/main/resources/Common/UI/Custom/Pages/MeineMod/ExamplePage.ui \
    --theme aether    # oder: classic (Gold/Dark-Fantasy-Hauptlane)
```

- **XAML ist die Quelle** — generierte `.ui` nie von Hand editieren, immer
  XAML aendern + neu generieren. Auto-validiert via KurashiEditor (Exit 2 = Errors).
- Event-Handler (`Click=`, `Toggled=`, ...) erzeugen `<out>.events.java` mit
  fertigen `addEventBinding`-Snippets fuer `InteractiveCustomUIPage.build()` —
  Snippets uebernehmen, Datei loeschen (Build excludet sie ohnehin).
- Sichtpruefung: `ui-render <out>.ui /tmp/x.png` | Layout-Check: `ui-measure <out>.ui --check-only`
- Subset-Doku: `xaml2ui` ohne Argumente | Beispiel-Korpus: `~/hytale_mods/design/kurashi_lib/_xaml-experiment/`
- `manifest.json`: bei UI-Mods `"IncludesAssetPack": true` setzen.
- Nur fuer Konstrukte ausserhalb des Subsets: erst `hy:`-Raw-Passthrough
  (`xmlns:hy="hytale"`) probieren, dann bewusst rohe `.ui` (ui-design-Skill).

## Projektstruktur

```
meine_mod/
├── build.gradle.kts              # Build-Config (Kotlin DSL)
├── settings.gradle.kts           # Mod-Name
├── gradle.properties             # daemon/parallel/caching aktiv
├── gradlew                       # Gradle Wrapper
├── libs/                         # Lokale JAR-Dependencies (compileOnly)
└── src/main/
    ├── java/de/kurashi/meinemod/
    │   ├── MeineMod.java         # Plugin-Entry (Lifecycle)
    │   ├── events/               # Event-Handler (extern delegiert)
    │   │   └── MeineEventHandler.java
    │   └── commands/             # Command-Klassen
    │       └── MeineCommand.java
    ├── xaml/
    │   └── ExamplePage.xaml      # UI-Quelle (XAML-first, generiert die .ui)
    └── resources/
        ├── manifest.json         # Hytale Mod Manifest (Website, DisabledByDefault)
        └── version.properties    # Version fuer Runtime-Zugriff
```

## Mod-Lifecycle

| Phase | Methode | Wann |
|-------|---------|------|
| 1 | Constructor | JAR geladen, `super(init)` aufrufen |
| 2 | `setup()` | Events + ECS registrieren, Server noch nicht bereit |
| 3 | `start()` | Server bereit, Commands + Scheduler starten |
| 4 | `shutdown()` | Server stoppt, alles aufraumen |

## Wichtig

- **Java 25** Toolchain (automatisch via `~/.jdks/`)
- **Kotlin DSL** (`build.gradle.kts`), niemals Groovy
- Server-API ist `compileOnly` (wird zur Laufzeit bereitgestellt)
- `shadowJar` bundelt nur `implementation`-Dependencies
- Deutsche Umlaute in Spieler-Texten verwenden (UTF-8)
- `${version}` in manifest.json wird beim Build automatisch ersetzt
- Bei bundled Dependencies: `relocate()` im shadowJar Block nutzen
- Events: `registerGlobal()` fuer String-keyed, `register()` fuer Void-keyed
- PlayerReadyEvent liefert `Player`, PlayerDisconnectEvent liefert `PlayerRef`

## Package-Layout Konvention

Das Template zeigt die empfohlene Struktur fuer bisher 12 aktive Mods:

- **Root-Package** (`de.kurashi.meinemod`) — Nur der Plugin-Entry (erweitert `JavaPlugin`).
- **`events/`** — Event-Handler als statische `registerAll(IEventRegistry)`-Methode gebuendelt. Logik in private static Methoden.
- **`commands/`** — Jede Command-Klasse eine Datei. `AbstractPlayerCommand` fuer Spieler-Commands, `AbstractCommand` falls Konsole erlaubt. Sub-Commands via `addSubCommand()` im Constructor.

Bei wachsenden Mods zusaetzliche Packages nach Domain (`ui/`, `db/`, `ecs/`, `util/`).

## Manifest-Felder

```json
"Website": "",              // Optional, fuer spaetere CurseForge-Veroeffentlichung
"DisabledByDefault": false, // Falls true: User muss per Command aktivieren
"ServerVersion": "*",       // Hytale-Mindest-Version, "*" = alle
"IncludesAssetPack": false  // true falls resources/ Hytale-Assets enthaelt
```
