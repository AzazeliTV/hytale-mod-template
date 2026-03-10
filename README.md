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
mkdir -p src/main/java/de/kurashi/meinemod
mv src/main/java/de/kurashi/template/TemplateMod.java \
   src/main/java/de/kurashi/meinemod/MeineMod.java
rm -rf src/main/java/de/kurashi/template
```

Package-Deklaration und Klassennamen in der Java-Datei anpassen.

### 4. Bauen + Deployen

```bash
chmod +x gradlew
./gradlew build
# JAR liegt in build/libs/MeineMod-1.0.0.jar

# Oder mit deploy-Tool:
deploy meinemod
```

## Projektstruktur

```
meine_mod/
├── build.gradle.kts            # Build-Config (Kotlin DSL)
├── settings.gradle.kts         # Mod-Name
├── gradlew                     # Gradle Wrapper
├── libs/                       # Lokale JAR-Dependencies (compileOnly)
└── src/main/
    ├── java/de/kurashi/...     # Mod-Code
    └── resources/
        ├── manifest.json       # Hytale Mod Manifest
        └── version.properties  # Version fuer Runtime-Zugriff
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
