# Talent Graph

Hytale mod to create and use talent graphs.

## Modules

| Module | Gradle plugin | Role |
|---|---|---|
| `api` | `java-library` | Public API. Published standalone as `dev.galysso.talentgraph:talent-graph-api`. |
| `core` | `com.azuredoom.hytale-tools` | Implementation, entry point, `manifest.json`. Ships one jar containing `api`. |

The root project applies `com.azuredoom.hytale-workspace`, which orchestrates the
workspace and propagates `hytaleVersion` / `patchline` / `manifestGroup`.

### Why `api` has no Hytale dependency

`api` compiles against the JDK alone — player identity is a `UUID`, progression
events are plain records, and subscription goes through `TalentListener` rather
than the server event bus. Two consequences worth knowing:

- The API does not break when a Hytale server upgrade changes a signature, and
  consumers do not inherit compile-time coupling to a server version.
- Anything that genuinely needs a server type (rendering, entity components)
  belongs in `core`, behind an API-level abstraction.

The Hytale server jar is injected on `compileOnly` by `hytale-tools`, which is
only applied to `core`. Applying it to `api` too is explicitly discouraged by
the Gradle plugin's docs unless the module is itself a loadable plugin.

`core`'s `jar` task copies `api`'s class output explicitly. This is required:
`hytale-tools` does not shade `project()` dependencies, so without it the
shipped plugin would be missing every API class at runtime.

## Development

Requires **JDK 25** — the Hytale Gradle plugin itself runs on it, so the Gradle
daemon must too, not just the compiler. `gradle/gradle-daemon-jvm.properties`
pins that requirement and Gradle provisions a JDK 25 on first run, so a system
JDK 21 on `PATH` is fine and CI needs no setup step.

Hot-swap debugging wants a JetBrains Runtime specifically:

```bash
JAVA_HOME=~/.local/share/JetBrains/Toolbox/apps/intellij-idea/jbr \
  ./gradlew runServer -Ddebug=true -Dhotswap=true
```

```bash
./gradlew setupHytaleDev      # first-time setup (fetches assets, prepares IDE sources)
./gradlew runServer           # local dev server
./gradlew build               # build the plugin jar
./gradlew updateAllPluginManifests   # regenerate core/src/main/resources/manifest.json
```

### Hytale assets

`setupHytaleDev` needs the game's `Assets.zip`. By default it runs an OAuth
**device flow**: it prints a URL and a code and blocks until you approve them in
a browser, then times out. Two ways through it:

- approve it — run the task in an interactive terminal, open the printed URL,
  sign in with the Hytale account that owns the game;
- skip it — point the build at an existing installation. Put the path in
  `~/.gradle/gradle.properties` (never in the repo, it is machine-specific):

  ```properties
  hytaleHomeOverride = /path/to/Hytale/install/release/package/game/latest/Assets.zip
  ```

  With the Flatpak launcher that path is under
  `~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/...`.

In game, `/talents` lists the registered graphs — the smoke test that the plugin
loaded and the API is reachable.

Identity and versions live in `gradle.properties`; `manifest.json` is generated
from it, so edit the properties rather than the manifest.

## Using the API from another plugin

Compile against the API without bundling it:

```kotlin
dependencies {
    compileOnly("dev.galysso.talentgraph:talent-graph-api:0.1.0")
}
```

Declare the runtime dependency so the server loads TalentGraph first:

```json
"Dependencies": { "Galysso:talentgraph": ">=0.1.0" }
```

Then register a graph during your own setup:

```java
TalentId toughness = new TalentId("mymod", "toughness");

TalentGraphApi.get().registry().register(
        TalentGraphBuilder.of(new TalentId("mymod", "warrior"), "Warrior")
                .talent(toughness, "Toughness", 3, rank -> rank)
                .talent(new TalentId("mymod", "cleave"), "Cleave", 1, rank -> 2,
                        Set.of(toughness))
                .build());
```

For an optional integration, declare it under `OptionalDependencies` and use
`TalentGraphApi.find()` instead of `get()`.

## API compatibility

`api` is versioned independently of the implementation and follows semantic
versioning. Within a major version:

- interfaces in `dev.galysso.talentgraph.api` gain methods only with a `default`
  body;
- `dev.galysso.talentgraph.api.internal` is not API and may change at any time.
