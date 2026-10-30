# Script Auto CE

Script Auto CE is a client-first Fabric mod for editing, running and sharing batch command scripts on
Minecraft 26.2. A matching server installation recompiles and validates every submitted source before
executing commands with the invoking player's normal permissions. When the server does not install the
mod, the client can fall back to dispatching commands directly through the vanilla command channel.

Current version: `0.1.0-alpha`.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.157.0 or newer for Minecraft 26.2
- Fabric Carpet 26.2
- Java 25 runtime

The build itself uses Gradle 9.2.1 and Fabric Loom 1.15.5. Gradle 9.2 requires a Java 25 JDK, so set
`JAVA_HOME` to a JDK 25 installation before building.

The project compiles against Fabric API `0.157.0+26.2` and declares no upper version bound. This keeps the
same JAR loadable with older 0.157 APIs and newer backward-compatible Fabric API releases such as 0.161.

If another installed mod requires a newer Fabric API than the server currently has, that conflict must be
resolved separately by updating Fabric API or removing the other mod. For example, a mod declaring
`fabric-api >=0.158.0` cannot run alongside Fabric API `0.157.0`.

## Build

```powershell
.\build.ps1
```

The remapped mod JAR is written to `build/libs/`.

`build.ps1` discovers `JDK25_HOME`, the launcher's Java 25 runtime, or an existing Java 25 installation.
For a manual build, set `JAVA_HOME` to a JDK 25 installation and run `.\gradlew.bat clean build`.
The build script also runs `scripts/check-privacy.ps1`, which rejects machine-specific absolute paths,
user-directory paths, hostnames and email addresses before a repository commit.

## Usage

- Press `F8` to open the script library.
- While typing `##` in chat, a colored command panel appears above the input; press `Tab` to complete
  the first matching command. Script IDs also complete after `##run`, `##share`, `##download` and `##stop`.
- Set the library shortcut from the mod settings screen or Controls. Both use the same Fabric
  `KeyMapping`, and changes are written to the normal Minecraft options.
- If Mod Menu is installed, Script Auto CE exposes the same settings screen from its mod entry.
- Use local, shared and running filters, or create and edit `.sace` scripts in the built-in editor.
- `##list`, `##run <name> [key=value...]`, `##stop`, `##status`, `##share <local>` and
  `##download <shared>` are intercepted by the client and are not sent to chat.
- Server operators can use `/scriptautoce list|run|status|stop|reload|config`.

Successful operations are green, warnings are yellow, errors are red, script log output is gray, and
status information is aqua.

Script files live under the game root in a deliberately visible directory:

```text
ScriptAutoCE/
  Scripts/       editable local scripts, nested folders and Unicode names supported
  Shared/        server shared-script storage
  Examples/      bundled examples
  Logs/          server audit logs
  LANGUAGE.md
  语言文档.md
```

Only settings remain under `config/scriptautoce`. Existing scripts from older versions are copied into
the new layout without deleting the originals. Existing files are never overwritten.

See [the bundled DSL guide](src/main/resources/assets/scriptautoce/docs/language.md) for syntax and safety
rules.

### Fake-player example

The fake-player example is copied into the first-run script directory as
`ScriptAutoCE/Scripts/示例/生成10个假人.sace`. It runs:

```text
let spawn = pos()
player sace_bot_<0..9> spawn at <captured spawn position>
```

Run it with `##run spawn_10_fake_players`. All ten Carpet fake players are spawned at the executing
player's exact position, captured once before the loop. Edit the `prefix` argument if those names are
already online.

## Direct fallback limitation

Without the server mod, Minecraft does not provide a structured command-denial acknowledgement. Direct
mode can enforce client-side syntax and policy checks but cannot reliably distinguish every permission
failure from chat output. Server-validated mode is required for reliable stop/skip behavior, sharing and
audit logs.
