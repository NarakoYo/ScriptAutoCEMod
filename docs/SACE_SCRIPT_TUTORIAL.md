# SACE 脚本教程

> SACE DSL 是 Script Auto CE 使用的受限 Minecraft 命令脚本语言。它可以把多条命令、循环、判断、等待、参数和只读查询组合成一个可重复运行的 `.sace` 脚本。

- 适用版本：`0.1.0-alpha`
- 目标游戏：`Minecraft 26.2`
- 脚本编码：`UTF-8`

## 教程目录

- [1. 教程概览](#1-教程概览)
- [2. 第一个脚本](#2-第一个脚本)
- [3. 文件与运行方式](#3-文件与运行方式)
- [4. 脚本结构](#4-脚本结构)
- [5. meta 元数据](#5-meta-元数据)
- [6. arg 参数](#6-arg-参数)
- [7. 值与类型](#7-值与类型)
- [8. let 与 set](#8-let-与-set)
- [9. 表达式与运算符](#9-表达式与运算符)
- [10. 字符串插值与安全规则](#10-字符串插值与安全规则)
- [11. 语句参考](#11-语句参考)
- [12. 内置函数总表](#12-内置函数总表)
- [13. 查询函数详解](#13-查询函数详解)
- [14. 安全编码函数详解](#14-安全编码函数详解)
- [15. 数学函数详解](#15-数学函数详解)
- [16. 完整示例](#16-完整示例)
- [17. 权限模型](#17-权限模型)
- [18. 命令策略](#18-命令策略)
- [19. 执行限制](#19-执行限制)
- [20. 常见错误与定位](#20-常见错误与定位)
- [21. 速查表](#21-速查表)

---

## 1. 教程概览

### 1.1 能做什么

SACE 脚本可以：

- 按顺序执行 Minecraft 命令，例如 `setblock`、`fill`、`tp`、`say`。
- 使用 `for` 循环批量生成建筑、区域、实体或连续命令。
- 使用 `if / else` 根据参数、方块、坐标或计分板结果决定后续操作。
- 使用 `wait` 按游戏刻暂停任务。
- 声明参数，让同一个脚本支持不同宽度、高度、材质和目标。
- 读取玩家位置、方块、在线玩家、附近实体和计分板值。
- 在脚本日志中输出进度、查询结果和错误信息。

### 1.2 不支持什么

首版 SACE 只提供内置函数，不支持：

- 用户自定义函数或类。
- Java、JavaScript、反射或动态类加载。
- 文件系统访问。
- 网络访问。
- 提权执行。
- 客户端断线后的任务恢复。
- 列表索引和列表循环。

### 1.3 一个完整脚本

```text
meta name "build_wall"
meta on_error "stop"
meta on_denied "stop"

arg width: int = 4 min 1 max 32
arg material: token = "minecraft:stone"

for x in range(0, width) {
    run "setblock ~${x} ~ ~ ${material}"
    wait 1t
}
```

---

## 2. 第一个脚本

在脚本库中新建 `.sace` 文件，然后输入：

```text
# 输出一条消息
meta name "hello_sace"

log "Hello from SACE"
run "say Script Auto CE is ready"
```

保存后有两种运行方式：

1. 打开脚本库，选择脚本并点击“运行”。
2. 在聊天栏输入 `##run hello_sace`。

脚本名称默认来自文件或 `meta name`。如果没有设置 `meta name`，系统会使用文件名。

---

## 3. 文件与运行方式

### 3.1 打开脚本库

默认按 `F8` 打开脚本库。快捷键可以在 Mod 设置页面或 Minecraft 控制设置中修改。

脚本库支持：

- 本地脚本。
- 服务器共享脚本。
- 正在运行或已中断的任务。
- 文件夹与脚本混排。
- 搜索、运行、编辑、上传、下载和删除。
- 新建文件和文件夹。

### 3.2 脚本目录

脚本位于游戏根目录下的 `ScriptAutoCE` 文件夹：

```text
ScriptAutoCE/
  Scripts/                本地脚本，可以创建多层文件夹
  Shared/                 服务端共享脚本
  Examples/               内置示例
  Logs/                   服务端审计日志
  LANGUAGE.md             简短语言参考
  语言文档.md             简短中文语言参考
  SACE脚本教程.html       完整 HTML 教程
  SACE脚本教程.md         当前 Markdown 教程
```

文件名、文件夹名和参数值支持 UTF-8。脚本扩展名必须是 `.sace`。

### 3.3 聊天指令

| 指令 | 作用 |
| --- | --- |
| `##list` | 获取脚本列表 |
| `##run <名称> [键=值...]` | 执行脚本 |
| `##stop` | 停止当前任务 |
| `##status` | 查看运行状态 |
| `##share <本地脚本>` | 上传脚本 |
| `##download <共享脚本>` | 下载共享脚本 |
| `##help` | 查看帮助 |

输入 `##` 后，聊天栏上方会显示带颜色的候选列表，按 `Tab` 可补全。

### 3.4 服务端指令

管理员或控制台可以使用：

```text
/scriptautoce list
/scriptautoce run <名称>
/scriptautoce status
/scriptautoce stop
/scriptautoce reload
/scriptautoce config
```

---

## 4. 脚本结构

一个 SACE 脚本通常包含三部分：

```text
# 1. 元数据
meta name "example"
meta on_error "stop"

# 2. 参数
arg width: int = 4 min 1 max 32

# 3. 执行语句
let material = "minecraft:stone"
for x in range(0, width) {
    run "setblock ~${x} ~ ~ ${material}"
    wait 1t
}
```

基本规则：

- 语句通常一行一条。
- 语句末尾的分号可选。
- 注释以 `#` 开始，持续到行尾。
- 字符串使用双引号。
- 代码块使用 `{` 和 `}`。
- 字符串插值使用 `${表达式}`。
- 变量名区分大小写。

---

## 5. meta 元数据

格式：

```text
meta 键 "静态值"
```

元数据值不能包含 `${...}` 插值。

| 键 | 可选值 | 作用 |
| --- | --- | --- |
| `name` | 任意文本 | 脚本显示名称 |
| `on_error` | `stop` / `continue` | 普通命令失败后停止或继续 |
| `on_denied` | `stop` / `skip` | 权限不足时停止或跳过命令 |
| `max_commands` | 正整数字符串 | 为当前脚本设置更低的命令数量上限 |
| `max_ticks` | 正整数字符串 | 为当前脚本设置更低的运行时长上限 |

示例：

```text
meta name "build_wall"
meta on_error "stop"
meta on_denied "skip"
meta max_commands "500"
meta max_ticks "1200"
```

### 5.1 on_error

`on_error "stop"` 是默认值。普通命令执行失败时，任务立即停止。

`on_error "continue"` 会在命令失败后记录日志并继续执行下一条命令。

### 5.2 on_denied

`on_denied "stop"` 是默认值。权限不足时任务停止。

`on_denied "skip"` 会跳过权限不足的命令，继续执行后面的命令。

危险命令策略、策略违规、权限提升尝试和调度器急停属于硬阻止，不能被 `skip` 绕过。

---

## 6. arg 参数

参数格式：

```text
arg 名称: 类型 = 默认值 [min 最小值] [max 最大值]
```

示例：

```text
arg width: int = 4 min 1 max 32
arg height: int = 3 min 1 max 16
arg material: token = "minecraft:stone"
arg target: selector = "@p"
arg message: string = "hello"
arg center: vec3 = "0 64 0"
arg enabled: bool = true
arg speed: double = 1.0 min 0.1 max 10
```

### 6.1 参数类型

| 类型 | 示例 | 可直接插入命令 | 说明 |
| --- | --- | --- | --- |
| `int` | `12` | 是 | 整数 |
| `double` | `1.5` | 是 | 小数 |
| `bool` | `true` / `false` | 是 | 布尔值 |
| `string` | `"hello world"` | 否 | 任意文本，必须使用 `quote()` 或 `json()` 编码 |
| `token` | `"minecraft:stone"` | 是 | 安全标识符 |
| `selector` | `"@a[tag=build]"` | 是 | Minecraft 实体选择器 |
| `vec3` | `"10 64 -2"` 或 `"10,64,-2"` | 是 | 三个坐标值 |

### 6.2 token 类型

`token` 只允许以下字符：

```text
字母、数字、下划线、点、冒号、加号、斜杠、等号、减号
```

适合表示：

- `minecraft:stone`
- `minecraft:deepslate_bricks`
- `#minecraft:logs`
- `namespace:value`

### 6.3 selector 类型

选择器必须以 `@a`、`@e`、`@p`、`@r` 或 `@s` 开头，可以包含 `[...]` 参数。

示例：

```text
@a
@p
@e[type=minecraft:zombie]
@a[tag=builder]
```

### 6.4 运行时覆盖参数

执行脚本时可以覆盖默认值：

```text
##run build_wall width=8 height=5 material=minecraft:stone
```

`min` 和 `max` 只对数值参数生效。传入值超出范围时，脚本会拒绝运行并显示诊断信息。

---

## 7. 值与类型

| 值类型 | 示例 | 用途 |
| --- | --- | --- |
| 整数 | `42`、`-3` | 坐标、次数、计分、数学计算 |
| 小数 | `1.5`、`-0.25` | 速度、距离、小数坐标 |
| 布尔值 | `true`、`false` | 条件判断 |
| 字符串 | `"hello"` | 文本、消息、参数 |
| 安全 token | `minecraft:stone` | 方块、物品、标签等单参数标识符 |
| 坐标 | `pos()` | 直接作为三坐标参数 |
| 选择器 | `@a[tag=builder]` | 表示命令目标 |
| 编码字符串 | `quote(text)` | 安全插入命令的文本参数 |
| 列表 | `players()` | 首版用于显示和日志 |

---

## 8. let 与 set

`let` 创建变量：

```text
let width = 8
let doubled = width * 2
```

`set` 修改已经存在的变量：

```text
set width = width + 1
```

规则：

- 变量名必须以字母或下划线开始。
- 变量名可包含字母、数字和下划线。
- 不允许用 `let` 重复声明同名变量。
- 使用未定义变量会产生脚本错误。
- 变量名区分大小写。

字符串支持以下转义：

| 转义 | 含义 |
| --- | --- |
| `\n` | 换行 |
| `\r` | 回车 |
| `\t` | 制表符 |
| `\"` | 双引号 |
| `\\` | 反斜杠 |

---

## 9. 表达式与运算符

表达式可以用于参数默认值、变量赋值、条件和字符串插值。

| 类别 | 运算符 | 示例 |
| --- | --- | --- |
| 括号与函数 | `()` | `(width + 1) * 2` |
| 单目 | `!`、`-` | `!enabled`、`-speed` |
| 乘除余 | `*`、`/`、`%` | `index % 2` |
| 加减 | `+`、`-` | `x + 1` |
| 比较 | `<`、`<=`、`>`、`>=` | `points >= 10` |
| 相等 | `==`、`!=` | `material != "minecraft:air"` |
| 逻辑与 | `&&` | `width > 0 && height > 0` |
| 逻辑或 | `||` | `enabled || width > 8` |

条件必须是布尔值。

正确：

```text
if count > 0 {
    log "有内容"
}
```

错误：

```text
if count {
    log "有内容"
}
```

除法或取余的除数为 `0` 时会产生脚本错误。

---

## 10. 字符串插值与安全规则

使用 `${表达式}` 把变量或函数结果插入字符串：

```text
arg name: string = "Alex"
arg radius: int = 5

let center = pos()
log "执行者=${name}, 半径=${radius}, 位置=${center}"

run "say ${quote(name)}"
run "fill ~-${radius} ~ ~-${radius} ~${radius} ~ ~${radius} minecraft:stone"
```

### 10.1 可以直接插入的值

- 整数。
- 小数。
- 布尔值。
- 坐标。
- 选择器。
- 安全 token。
- `quote()`、`json()` 等编码函数的结果。

### 10.2 不能直接插入的值

普通 `string` 不能直接放进 `run` 命令。

错误：

```text
arg message: string = "hello world"
run "say ${message}"
```

正确：

```text
arg message: string = "hello world"
run "say ${quote(message)}"
```

`log` 允许直接显示字符串，因为它不会执行命令。

### 10.3 列表限制

`players()` 和 `entities()` 返回列表。首版可以把列表写入日志，但不支持：

- 列表索引。
- 列表循环。
- 直接拼成单个命令参数。

---

## 11. 语句参考

### 11.1 run

签名：

```text
run "Minecraft 命令"
```

执行一条 Minecraft 命令。命令开头不需要写 `/`。

```text
run "time set day"
run "weather clear"
run "setblock ~ ~1 ~ minecraft:glowstone"
run "execute as @a at @s run say hello"
```

每条 `run` 只执行一条命令。

命令使用发起脚本玩家的原版权限执行，不会自动获得 OP。

服务器策略会递归检查：

```text
execute ... run <内层命令>
```

### 11.2 log

签名：

```text
log "输出文本"
```

向任务日志输出文本，不执行命令。

```text
arg target: selector = "@p"

log "开始生成"
log "当前位置：${pos()}"
log "目标：${target}"
```

脚本日志会显示在运行状态和任务详情中。

### 11.3 wait

签名：

```text
wait 20t
```

暂停指定游戏刻后继续执行。20 刻通常等于 1 秒。

```text
run "setblock ~ ~ ~ minecraft:redstone_block"
wait 2t
run "setblock ~ ~ ~ minecraft:air"
```

规则：

- 必须是字面整数。
- 范围是 1 到 72000。
- 末尾的 `t` 可选。
- 等待不会阻塞服务器主线程。

### 11.4 if / else

签名：

```text
if 条件 {
    ...
} else {
    ...
}
```

示例：

```text
arg enabled: bool = true
let points = score("points", "@p")

if enabled && points >= 10 {
    run "say 条件满足"
} else if enabled {
    run "say 分数不足"
} else {
    log "脚本已关闭"
}
```

### 11.5 for / range

签名：

```text
for 变量 in range(起点, 终点[, 步长]) {
    ...
}
```

终点不包含在循环内。默认步长为 `1`。

```text
for x in range(0, 5) {
    run "setblock ~${x} ~ ~ minecraft:stone"
}
```

倒序循环：

```text
for y in range(3, 0, -1) {
    run "setblock ~ ~${y} ~ minecraft:glass"
}
```

规则：

- 循环变量是整数。
- 步长不能为 `0`。
- 不要在循环体内用 `set` 修改循环变量。
- 循环次数过多会触发命令数、运行时长或解释器操作上限。

### 11.6 fail

签名：

```text
fail "错误说明"
```

立即以脚本错误结束任务。

```text
arg width: int = 4 min 1 max 32

if width > 16 {
    fail "宽度过大，请缩小范围"
}
```

### 11.7 return

签名：

```text
return
```

立即以正常完成状态结束任务。

```text
run "say 开始"
return
run "say 这行不会执行"
```

---

## 12. 内置函数总表

首版只有以下内置函数。函数名区分大小写，参数数量必须完全匹配。

| 函数 | 返回 | 作用 | 能否直接插入命令 |
| --- | --- | --- | --- |
| `pos()` | `vec3` | 执行脚本玩家的当前位置 | 是 |
| `block(x, y, z)` | `token` | 读取指定坐标的方块 ID | 是 |
| `players()` | 玩家名列表 | 获取在线玩家名 | 否 |
| `entities(type, radius)` | UUID 列表 | 获取附近实体 UUID | 否 |
| `score(objective, target)` | `int` | 读取计分板值 | 是 |
| `token(value)` | `token` | 校验并转换成安全 token | 是 |
| `quote(value)` | 编码字符串 | 添加双引号并转义文本 | 是 |
| `json(value)` | JSON 字符串 | 把文本编码成 JSON 字符串 | 是 |
| `min(a, b)` | `double` | 取较小值 | 是 |
| `max(a, b)` | `double` | 取较大值 | 是 |
| `abs(value)` | `int` / `double` | 取绝对值 | 是 |
| `round(value)` | `int` | 四舍五入到整数 | 是 |

---

## 13. 查询函数详解

### 13.1 pos()

签名：

```text
pos() -> vec3
```

返回脚本执行时玩家所在的位置，格式为 `x y z`。

```text
let spawn = pos()
log "保存位置：${spawn}"
run "tp @p ${spawn}"
run "setblock ${spawn} minecraft:stone"
```

如果希望十个假人都在同一位置，应在循环前只调用一次 `pos()`：

```text
let spawn = pos()
for i in range(0, 10) {
    run "player bot_${i} spawn at ${spawn}"
}
```

### 13.2 block(x, y, z)

签名：

```text
block(x, y, z) -> token
```

读取执行者所在世界中的方块 ID。

`~` 相对坐标以玩家位置为基准。

```text
let below = block("~", "~-1", "~")
log "脚下方块：${below}"

if below == "minecraft:air" {
    fail "玩家脚下没有方块"
}

run "setblock ~ ~-1 ~ minecraft:stone"
```

限制：

- 支持绝对坐标。
- 支持 `~` 相对坐标。
- 不支持 `^` 局部坐标。

### 13.3 players()

签名：

```text
players() -> 玩家名列表
```

返回当前在线玩家名，并按名称排序。

```text
log "在线玩家：${players()}"
```

首版列表只适合日志显示，不能直接作为单个命令参数。

### 13.4 entities(type, radius)

签名：

```text
entities(type, radius) -> UUID 列表
```

返回执行玩家附近实体的 UUID。

```text
log "附近僵尸：${entities("minecraft:zombie", 16)}"
log "附近全部实体：${entities("*", 8)}"
```

规则：

- 半径范围限制在 `0` 到 `64`。
- 最多返回 `256` 条结果。
- 类型可以写完整 ID：`minecraft:zombie`。
- 类型也可以写短名：`zombie`。
- `*` 或 `any` 表示不过滤类型。

首版列表只适合日志显示，不能直接作为单个命令参数。

### 13.5 score(objective, target)

签名：

```text
score(objective, target) -> int
```

读取计分板目标中的分数。目标或分数不存在时返回 `0`。

```text
arg player: string = "Alex"
let points = score("points", player)

if points >= 100 {
    run "say ${quote(player)} 已达到 100 分"
} else {
    log "当前分数：${points}"
}
```

---

## 14. 安全编码函数详解

### 14.1 token(value)

签名：

```text
token(value) -> token
```

把值转换成安全命令 token。

适合：

- 方块 ID。
- 物品 ID。
- 标签名。
- 命名空间 ID。
- 安全的单参数标识符。

```text
arg id: token = "minecraft:stone"
run "setblock ~ ~ ~ ${token(id)}"
```

包含空格、引号或特殊字符时会拒绝执行。

### 14.2 quote(value)

签名：

```text
quote(value) -> 带双引号的命令参数
```

给文本添加双引号，并转义反斜杠、双引号和换行。

适合：

- `say` 消息。
- `tell` 消息。
- 标题文本。
- 普通命令中的文本参数。

```text
arg name: string = "Alex"
run "say ${quote(name)}"
```

### 14.3 json(value)

签名：

```text
json(value) -> JSON 字符串
```

把文本编码成 JSON 字符串。

```text
arg name: string = "Alex"
run "tellraw @a {\"text\":${json(name)}}"
```

`json()` 只负责编码字符串值。外层 JSON 结构仍需要自己写正确。

### 14.4 安全规则

错误：

```text
arg message: string = "say hello"
run "say ${message}"
```

正确：

```text
arg message: string = "say hello"
run "say ${quote(message)}"
```

错误写法可能让未经校验的文本变成额外命令参数，因此会被解释器拒绝。

---

## 15. 数学函数详解

### 15.1 min(a, b)

```text
min(a, b) -> double
```

返回较小值。

```text
let safeRadius = min(radius, 16)
```

### 15.2 max(a, b)

```text
max(a, b) -> double
```

返回较大值。

```text
let largest = max(width, height)
```

### 15.3 abs(value)

```text
abs(value) -> int / double
```

返回绝对值。

```text
let distance = abs(-12)
```

### 15.4 round(value)

```text
round(value) -> int
```

四舍五入到整数。

```text
let blocks = round(4.6)
```

### 15.5 组合示例

```text
let safeRadius = min(radius, 16)
let largest = max(width, height)
let distance = abs(-12)
let blocks = round(4.6)

run "fill ~-${safeRadius} ~ ~-${safeRadius} ~${safeRadius} ~ ~${safeRadius} minecraft:stone"
log "最大边=${largest}, 距离=${distance}, 方块数=${blocks}"
```

---

## 16. 完整示例

### 16.1 在同一位置生成 10 个 Carpet 假人

需要安装 Carpet，并拥有执行 `/player` 命令的权限。

```text
meta name "spawn_10_fake_players"
meta on_error "continue"
meta on_denied "stop"
meta max_commands "10"
meta max_ticks "200"

arg prefix: token = "sace_bot"

log "开始在同一位置生成 10 个假人"
let spawn = pos()

for i in range(0, 10) {
    run "player ${prefix}_${i} spawn at ${spawn}"
    wait 1t
}

log "生成完成。清理假人可使用 /player 名称 kill"
```

关键点是 `let spawn = pos()` 必须写在循环外。这样十次命令都会使用同一个坐标。

如果已经存在同名前缀的假人，可以更换前缀：

```text
##run spawn_10_fake_players prefix=test_bot
```

### 16.2 生成一堵石墙

```text
meta name "build_wall"
meta on_error "stop"
meta on_denied "stop"
meta max_commands "2048"

arg width: int = 8 min 1 max 64
arg height: int = 4 min 1 max 32
arg material: token = "minecraft:stone"
arg gap: int = 0 min 0 max 4

log "开始生成 ${width}x${height} 的墙"

for x in range(0, width) {
    for y in range(0, height) {
        run "setblock ~${x + gap} ~${y} ~ ${material}"
    }
    wait 1t
}

log "墙生成完成"
```

覆盖参数：

```text
##run build_wall width=12 height=5 material=minecraft:deepslate_bricks
```

### 16.3 安全发送玩家文本

```text
meta name "safe_message"

arg message: string = "hello world"
arg target: selector = "@a"

run "say ${quote(message)}"
run "tellraw ${target} {\"text\":${json(message)}}"
log "消息已发送"
```

不要直接写：

```text
run "say ${message}"
```

应使用：

```text
run "say ${quote(message)}"
```

### 16.4 根据分数字段决定执行内容

```text
meta name "score_gate"

arg objective: token = "points"
arg target: string = "Alex"
arg required: int = 10 min 0 max 100000

let points = score(objective, target)

if points >= required {
    run "say ${quote(target)} 已通过"
} else {
    log "当前分数：${points}, 需要：${required}"
}
```

---

## 17. 权限模型

- 所有命令使用发起玩家当前的原版权限。
- 脚本不会把普通玩家提升为 OP。
- 脚本不会把命令切换成控制台身份。
- 玩家失去权限时，命令会按服务器结果被拒绝。
- 客户端直连模式无法可靠获得结构化的权限拒绝结果。
- 服务端安装 Script Auto CE 时，拒绝原因和任务状态更准确。
- 玩家断线、退出、主动停止或服务器急停时，正在运行的任务会结束。

---

## 18. 命令策略

服务器默认阻止高风险命令根：

```text
stop
op
deop
ban
ban-ip
pardon
pardon-ip
whitelist
kick
reload
datapack
function
schedule
jfr
perf
debug
save-off
save-on
save-all
tick
transfer
```

策略会检查命令根，并递归检查：

```text
execute ... run <命令>
```

危险命令、策略违规和调度器急停属于硬阻止，即使设置：

```text
meta on_denied "skip"
```

也不会继续执行。

管理员可以通过服务端设置或以下指令管理策略：

```text
/scriptautoce config
```

普通脚本不能自行解除服务器限制。

---

## 19. 执行限制

| 项目 | 默认或硬限制 | 说明 |
| --- | --- | --- |
| 源码大小 | 64 KiB | 上传和编译的最大源码大小 |
| 编译指令数 | 2,000 | 编译器生成的内部指令数量 |
| 解释器操作数 | 1,000,000 | 单次运行的解释器操作硬限制 |
| 单次命令数 | 默认 10,000 | 服务器可调低，脚本元数据只能设置更低值 |
| 每刻命令数 | 默认 4 | 防止一次执行大量命令导致卡顿 |
| 单次运行时长 | 默认 6,000 刻 | 约 5 分钟，包含等待时间 |
| 并发任务 | 每玩家 1，全局 8 | 管理员可调整 |
| 单次 wait | 72,000 刻 | 约 1 小时 |

如果遇到以下错误：

- 命令数量超过阈值。
- 运行时间超过阈值。
- 解释器操作数量超过限制。
- 调度器急停。
- 任务被服务器中断。

可以尝试：

1. 缩小循环范围。
2. 减少一次执行的命令数量。
3. 将大任务拆成多个脚本。
4. 减少不必要的 `wait`。
5. 请管理员检查服务器并发和执行上限。

---

## 20. 常见错误与定位

| 提示或现象 | 常见原因 | 解决方式 |
| --- | --- | --- |
| `Unknown variable` | 变量未声明或名称拼写错误 | 先使用 `let` 声明，再检查大小写 |
| `Expected a whole number` | 把小数传给了只接受整数的位置 | 使用 `round()` 或改为 `double` |
| `This value cannot be inserted` | 普通字符串直接进入 `run` | 使用 `quote()` 或 `json()` |
| `Unknown function` | 函数名拼错或使用了不存在的自定义函数 | 只使用本文列出的内置函数 |
| `Loop step cannot be zero` | `range()` 的步长为 `0` | 正序用正数，倒序用负数 |
| `Command root is blocked` | 命令被服务器策略阻止 | 更换普通命令或联系管理员 |
| `Runtime tick limit exceeded` | 等待时间或运行时间过长 | 缩小任务或拆分脚本 |
| `Command limit exceeded` | 实际执行命令数达到上限 | 减少循环次数 |
| 权限不足 | 玩家没有对应原版命令权限 | 由服主给予原版权限或调整脚本 |
| 任务中断 | 玩家断线、主动停止、服务器急停或异常 | 打开“运行中”详情查看中断原因 |

编译器诊断会显示行号和列号。先定位到对应行，再检查：

- 括号是否闭合。
- 字符串是否缺少结束双引号。
- 是否使用了未知函数。
- 参数类型是否匹配。
- 是否把普通字符串直接插入命令。

---

## 21. 速查表

### 21.1 声明

```text
meta name "demo"
meta on_error "stop"
meta on_denied "skip"

arg count: int = 4 min 1 max 32
arg material: token = "minecraft:stone"

let total = count * 2
set total = total + 1
```

### 21.2 流程

```text
if total > 4 {
    log "large"
} else {
    log "small"
}

for i in range(0, count) {
    wait 1t
}

fail "停止并报错"
return
```

### 21.3 命令与日志

```text
log "位置：${pos()}"
run "say ${quote(message)}"
run "setblock ~ ~ ~ ${material}"
run "tellraw @a {\"text\":${json(message)}}"
```

### 21.4 常用函数

```text
pos()
block("~", "~-1", "~")
players()
entities("minecraft:zombie", 16)
score("points", "Alex")
token(value)
quote(value)
json(value)
min(a, b)
max(a, b)
abs(value)
round(value)
```

### 21.5 常用参数类型

```text
int
double
bool
string
token
selector
vec3
```

---

## 相关文档

- [HTML 交互版教程](SACE_SCRIPT_TUTORIAL.html)
- [仓库 README](../README.md)
- [简短 DSL 参考](../src/main/resources/assets/scriptautoce/docs/language.md)

---

Script Auto CE · SACE DSL 中文教程 · 适用于 `0.1.0-alpha`
