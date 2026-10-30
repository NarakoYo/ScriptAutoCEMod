# SACE DSL

For the complete Chinese tutorial with examples and function reference, open `SACE脚本教程.html`
or `SACE脚本教程.md` from the game directory or the repository's `docs` folder.

SACE DSL is a restricted command language. It never evaluates Java, JavaScript, files or network calls.

## Metadata

```text
meta name "wall"
meta on_error "stop"
meta on_denied "skip"
meta max_commands "128"
meta max_ticks "1200"
```

`on_error` accepts `stop` or `continue`. `on_denied` accepts `stop` or `skip`.

## Arguments

```text
arg width: int = 5 min 1 max 32
arg material: token = "minecraft:stone"
arg center: vec3 = "0,64,0"
arg target: selector = "@p"
```

Supported types: `int`, `double`, `bool`, `string`, `token`, `vec3`, `selector`.

## Values and control flow

```text
let total = width * 2
set total = total + 1

if total > 4 {
    log "large"
} else {
    log "small"
}

for x in range(0, width) {
    run "setblock ~${x} ~ ~ ${material}"
    wait 1t
}
```

## Commands and interpolation

`run` accepts one Minecraft command. Numbers, booleans, coordinates, selectors and `token` values can be
inserted directly. Plain `string` values must be converted with `quote(value)` or `json(value)`.

```text
run "say hello ${playerName}"
run "tellraw @a {\"text\":${json(playerName)}}"
```

## Read-only functions

- `pos()` returns the executor position.
- `block(x, y, z)` returns a block id. Relative `~` coordinates are supported.
- `players()` returns online player names.
- `entities(type, radius)` returns nearby entity UUIDs.
- `score(objective, target)` returns a scoreboard value.
- `token(value)`, `quote(value)`, `json(value)` produce safe command tokens.
- `min(a,b)`, `max(a,b)`, `abs(value)`, `round(value)`.
- `floor(value)`, `ceil(value)`, `clamp(value,min,max)` for bounded numeric work.
- `length(value)`, `contains(value,search)`, `startsWith(value,prefix)`, `endsWith(value,suffix)`.
- `lower(text)`, `upper(text)`, `trim(text)`, `replace(text,search,replacement)`, `join(list,separator)`.

## Safety

The server recompiles every submitted source, checks command policies and executes commands with the
invoking player's normal Minecraft permissions. It never elevates permission. Scripts stop when they
exceed instruction, command, tick, concurrency or emergency tick-time limits.
