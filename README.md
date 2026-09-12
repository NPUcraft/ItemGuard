# ItemGuard

**中文（默认）** · [English](README.en.md)

仓库：https://github.com/NPUcraft/ItemGuard

面向 Paper 服的物品安全、物品流动监测与经济异常检测插件。

ItemGuard 会检测异常物品流动、非法或可疑物品，以及复制（dupe）相关迹象，并向管理组提供可解释的证据。它**不能**拦截所有复制、**不保证**防护所有漏洞，也**永远不会**自动封禁、踢出、没收物品或回滚背包。

> ItemGuard 检测异常物品流动、非法/可疑物品以及复制迹象。  
> 它不保证能预防或发现每一种复制漏洞。

当前版本：

- **已公开发布 RC2：** `1.0.0-RC2`（GitHub prerelease [`v1.0.0-RC2`](https://github.com/NPUcraft/ItemGuard/releases/tag/v1.0.0-RC2)）。该制品**不包含**误报 hardening。
- **当前 Field Test 预发布：** `1.0.0-RC3-SNAPSHOT`（GitHub prerelease [`v1.0.0-RC3-SNAPSHOT`](https://github.com/NPUcraft/ItemGuard/releases/tag/v1.0.0-RC3-SNAPSHOT)）。**不是** `1.0.0-RC3`，也不是正式 1.0.0。

不要把 GitHub 上的 RC2 当成已经包含 incident 引擎 / SourceHint hardening。

## ItemGuard 会做什么

- 扫描背包中 Paper 实际能暴露的非法或可疑物品数据
- 监视物品移动（拾取、容器、合成、死亡、潜影盒、收纳袋、创造模式）
- 在 Bukkit 事件或已接入的集成能解释时，把获得归因到预期来源
- 对仍无法解释或非法的模式打分，并通知管理组
- 为管理员保留一段较短的内存时间线（inspect / trace）

## ItemGuard 不会声称什么

- 不会预防或发现每一种复制漏洞
- 不是反作弊、反矿物透视或战斗监控
- 不会改写其他插件的背包
- 不连接 MySQL、Redis 或 Web 控制台
- 支持 HuskSync 只表示能识别**合法的同步还原**，不表示能抓住每一次跨服复制

## 功能

- **非法物品扫描** — 超等级附魔、超堆叠、非法耐久、可疑属性/组件、嵌套容器内容
- **物品流动监测** — 预期流动记账 + 背包对账
- **风险引擎** — 可解释信号，实时 0–100 分
- **Trace / Inspect** — 管理组用的内存时间线与会话摘要
- **潜影盒监测** — 高流量与快速转移信号
- **感知 HuskSync** — 把官方背包 apply 视为可解释来源
- **取证日志** — 长期 JSONL 文件，供事后排查（与内存中的 `/ig trace` 分开）

## 运行要求

- **Paper 1.21.8**（目标版本；其他 1.21.x 不保证）
- **Java 21**
- 可选：同一 Paper 服上的 **HuskSync 3.8.7**

## 安装

1. 安装 Java 21 与 Paper 1.21.8。
2. 把 JAR 放到 Paper 的 `plugins/` 目录。误报 hardening Field Test 请用 `ItemGuard-1.0.0-RC3-SNAPSHOT.jar`。GitHub 上的 `ItemGuard-1.0.0-RC2.jar` 仍是不含 hardening 的已发布 RC2。
3. 启动一次服务器。若配置文件尚不存在，ItemGuard 会把默认 YAML 复制到 `plugins/ItemGuard/`。
4. 按需修改这些文件，然后执行 `/ig reload`。
5. 给管理组 `itemguard.admin` 权限（默认：OP）。

GitHub 上的 `1.0.0-RC2` 是已发布的候选版。本仓库当前构建是 **1.0.0-RC3-SNAPSHOT**（Field Test 候选，不是 RC3 发布）。请先在测试服使用。注意自定义物品以及经济/奖励类插件带来的误报。

## 从 RC1 升级

**不要删除**现有配置文件。

1. 停止服务器
2. 备份整个 `plugins/ItemGuard/`
3. 用 `ItemGuard-1.0.0-RC2.jar` 替换 RC1 的 JAR
4. 启动服务器
5. 查看新出现的 `logging.yml`
6. 用 `/ig status` 确认版本与取证日志状态

仅当缺失时才会补上 `logging.yml` 和 `logs/`。你已有的 YAML 会按原样保留（字节级不变）；缺的键使用内存默认值。除非你打算重置，否则不要为了“干净安装”删掉旧配置目录。

从本 RC 再升到后续版本前，请再次备份 `plugins/ItemGuard/`。

**配置兼容**（RC1 形态的 YAML + RC2 JAR）**不等于**“启动过原始已发布 RC1 JAR 再换成 RC2”。精确制品升级仍需要那份原始文件（SHA-256 `11581d2fa2a61198a491ef680181f1fc5041e413c54fdbb1f0c54c3d5eeda87b`）。当前 workspace 尚未完成 **EXACT_ARTIFACT_UPGRADE**。

## HuskSync 兼容性

HuskSync 是可选的（`softdepend`）。若已安装且 `integrations.yml` 中 `husksync.enabled: true`，ItemGuard 会自动加载集成。若没有 HuskSync，ItemGuard 照常启用，只是跳过该集成。

**已实测：** HuskSync **3.8.7** + Paper **1.21.8**。其他 HuskSync 版本不作保证。

ItemGuard 能识别 HuskSync 的背包数据应用，避免把合法的同步还原当成无法解释的物品获得。它仍可能标出物品流动上的可疑模式，但不保证发现每一次跨服复制。

ItemGuard **从不**读取 HuskSync 数据库、Redis 或 Velocity 转发密钥。

## 命令

根命令：`/itemguard`  
别名：`/ig`

| 命令 | 说明 |
| --- | --- |
| `/ig help` | 列出发送者可用的命令 |
| `/ig status` | 插件、Paper、扫描器、流动、风险、HuskSync、取证日志，以及 Active Incidents / High / Critical |
| `/ig inspect <玩家>` | 实时风险 / 流动 / HuskSync 摘要 |
| `/ig trace <玩家> [时长] [物品]` | 内存时间线（默认 `5m`） |
| `/ig scan <玩家>` | 扫描当前背包 |
| `/ig alerts` | 最近的管理组警报 |
| `/ig reload` | 重载 YAML，不重置玩家基线 |
| `/ig debug <玩家>` | 管理诊断转储（不含 NBT / PDC / 密钥） |

时长示例：`30s`、`5m`、`10m`、`30m`、`1h`。

`/ig debug` 仅供管理员诊断，不会打印完整 PDC、NBT、数据库密码、Redis 凭据或 Velocity 密钥。

## 权限

全部默认 `op`。`itemguard.admin` 包含其余权限。

- `itemguard.admin`
- `itemguard.status`
- `itemguard.inspect`
- `itemguard.trace`
- `itemguard.scan`
- `itemguard.alerts`
- `itemguard.reload`
- `itemguard.debug`

## 配置

首次启动时创建，**之后不会覆盖**：

- `config.yml` — 对账间隔、心跳（游戏刻）、预期记账 TTL、扫描防抖、嵌套深度
- `scanner.yml` — 非法 / 可疑 / 自定义物品规则与 PDC 命名空间
- `risk.yml` — 信号分数、TTL、警报阈值
- `items.yml` — **风险权重**，不是货币价格
- `alerts.yml` — 管理组警报投递（无自动处罚）
- `integrations.yml` — HuskSync 开关
- `messages.yml` — MiniMessage 文案（目前为英文）
- `logging.yml` — 异步取证 JSONL（警报、重要事件、管理操作）

非法取值会在控制台警告并回退到默认值，文件本身按你写的保留。

### 建议的服类型配置（需手工改 YAML）

没有图形化配置切换。按下面思路改 YAML：

**原版生存** — 使用自带默认。警报阈值 60，不要开自动处罚。

**半原版** — 保留扫描器的 INVALID 规则；商店/抽奖若直接发物品，那些插件应调用 `ItemGuardApi.recordExpectedGain`，否则会出现无法解释的获得。

**RPG / 自定义物品服** — 默认 `CUSTOM_ITEM_METADATA`、`COMPONENT_MODIFIED`、`CUSTOM_MAX_STACK` 已经是 0 分。仍建议把物品插件命名空间加到 `scanner.yml` 的 `persistent-data.ignored-namespaces`。除非你真的允许超堆叠/超等级附魔，否则不要关掉 `OVERSIZED_STACK` / `OVER_LEVEL_ENCHANTMENT`。

## 风险打分如何工作

每个检测器发出可解释信号，信号进入**独立 Incident**。玩家当前风险是 **MAX(未过期 Incident 分数)**，不是把过去 120 秒里所有信号加在一起。

同一波物品流（例如 UNKNOWN 钻石 + 高价值爆发 + 重复相同签名）可以组合进同一个 `ITEM_GAIN` incident。无关事件（可疑靴子、已验证的潜影盒整理、圆石拾取）不会互相叠分。

- 默认管理组警报：**Incident 从 &lt;60 跨到 ≥60** 发一次 HIGH
- 危急：**从 &lt;80 跨到 ≥80** 发一次 CRITICAL
- 已经 CRITICAL 的 incident 不会因为每一笔新 Flow 再刷屏
- 默认策略：**先警报，永不自动处罚**
- UNKNOWN 是归因状态，不是作弊结论。低价值无法解释获得只记取证，默认风险 0–5

默认分值（不是完整列表）：

| 信号 | 默认 |
| --- | --- |
| `OVERSIZED_STACK` | 45 |
| `OVER_LEVEL_ENCHANTMENT` | 40 |
| `UNEXPLAINED_ITEM_GAIN` | 按物品价值/数量（约 3–35），不再固定 +35 |
| `HIGH_VALUE_ITEM_BURST` / `RARE_ITEM_BURST` | 20，且要求 `item-value >= 20` |
| `CUSTOM_ITEM_METADATA` | 0 |
| `COMPONENT_MODIFIED` | 0 |
| `SHULKER_RAPID_TRANSFER` | 0（单独的快速转移不构成警报） |
| `HUSKSYNC_DATA_APPLY` | 0 |
| 创造模式背包（`CREATIVE_INVENTORY`） | 0 |
| 已知容器来源 | 0 |

扫描器对同一 `player + ItemSignature + FindingType` 只贡献一次风险；重复扫描只更新 lastSeen/slot。

GUI source hint 默认 TTL 为 **250ms**（tick 级），精确 pickup credit 仍为 2500ms。

`items.yml` 的数值是**爆发检测用的风险权重**，不是经济价格表。

## 取证日志

ItemGuard 把选定的高价值证据写成 `plugins/ItemGuard/logs/` 下的 **JSONL**（一行一个 JSON 对象），供事后调查。它**不能替代** `/ig trace`；后者仍是较短的内存时间线。

默认目录：

```text
plugins/ItemGuard/logs/
├── alerts/   HIGH / CRITICAL 管理组警报
├── events/   UNKNOWN_GAIN、扫描结果、HuskSync apply、高价值流动
└── admin/    ItemGuard 管理命令，例如 /ig scan
```

按日轮转（`2026-09-11.jsonl`）；单文件超过 `max-file-size-mb` 再切分（`2026-09-11-1.jsonl`）。超过 `retention-days`（默认 30）的文件会被删除。`logging.yml` 里的 `server-name` 会打到每一行，方便合并多台 HuskSync 后端的日志。

**默认会记录：** 管理组警报、无法解释的获得、非法物品、中/高可疑扫描结果、HuskSync 数据应用摘要、管理操作（`scan` / `inspect` / `trace` / `reload` / `debug` / `alerts`），以及 `items.yml` 权重不低于 `high-value-flow.minimum-item-value`（默认 30）的物品流动。钻石权重是 25，因此普通捡钻石不会写入。

**默认不记录：** 普通圆石/箱子/合成流量、玩家聊天、登录 IP、完整 NBT 或 ItemStack 字节、PDC 值、背包转储、数据库/Redis/Velocity 密钥、`/ig help`、`/ig status`。本版本没有 `/ig logs` 搜索命令。

写入是异步的：Paper 主线程只入队一份不可变记录，由单独的写线程追加 JSONL。队列满时会丢弃低优先级记录，并按频率限制打印控制台警告；检测本身继续。磁盘错误只让日志降级。

正常关服会排空并刷出队列中的记录。JVM 崩溃、强制杀进程或主机故障，可能丢失少量尚未刷盘的记录。ItemGuard 不会对每条记录都 fsync。

设置 `logging.enabled: false` 可关闭持久化。`directory` 必须落在插件数据目录内；路径穿越会回退到 `logs`。

## 误报理念

ItemGuard 宁可抓住无法解释的高价值流动，也不选择沉默。产品是管理组警报；自动处罚不在范围内。

常见、可预期的噪音：

- `/give` 以及未走 `ItemGuardApi` 就调用 `addItem` 的插件 → `UNKNOWN`
- 带名称、lore、CustomModelData 或 PDC 的自定义物品 → `CUSTOM`（低分，有家族上限）
- 创造模式生成物品 → 标记为创造，风险 0，扫描器仍会运行

按服类型调整 `risk.yml` / `scanner.yml`，而不是关掉整个插件。

## 扫描器分类

| 类别 | 含义 |
| --- | --- |
| `INVALID` | 不可能是合法原版/Paper 物品的数据（超等级、超堆叠、非法耐久） |
| `SUSPICIOUS` | 不寻常，但不自动等于作弊 |
| `CUSTOM` | 合法自定义物品标记（名称、lore、CustomModelData、未知 PDC） |
| `INFO` | 仅供参考 |

未知 PDC 命名空间为 `CUSTOM`。明确拒绝的命名空间为 `SUSPICIOUS`。原版 `minecraft` / `bukkit` / `paper` 命名空间会被忽略。ItemGuard 不附带庞大的第三方白名单。

## 第三方插件 / ItemGuard API

如果其他插件把物品**直接**放进玩家背包，请在物品出现**之前**登记这次获得：

```java
import com.npucraft.itemguard.api.ItemGuardApi;
import com.npucraft.itemguard.api.ItemGuardApiProvider;

if (ItemGuardApiProvider.isAvailable()) {
    ItemGuardApi api = ItemGuardApiProvider.get();
    api.recordExpectedGain(player.getUniqueId(), "DIAMOND", 16, "MyCratePlugin", rewardId);
}
```

还有 `recordExpectedGain(UUID, ItemStack, int, String, String)`，以及用于成组发放的 `beginTransaction` / `endTransaction`。

`ExternalItemSourceProvider` 留给以后的官方提供者注册表。ItemGuard 1.0 **不会**自动发现商店/抽奖/ItemsAdder 等插件。

## 性能

默认不是“每个玩家每 tick 都扫一遍”：

- 脏玩家每 **20 游戏刻**（1 秒）对账一次
- 静默变更心跳每 **40 游戏刻**（约 2 秒）
- 背包扫描防抖：每玩家 **1500ms**
- 嵌套容器 / 收纳袋扫描深度 **3**
- Trace 保留 **30 分钟**，每玩家 2000 条事件

## 测试

ItemGuard 当前开发构建（`1.0.0-RC3-SNAPSHOT`）的自动化覆盖包括：

- 单元测试（纯 Java）
- 真实 Paper **1.21.8** + Mineflayer 协议 **772**
- 箱子、Shift 点击、部分 Shift 点击、合成、死亡、收纳袋、潜影盒、创造模式
- 非法物品扫描
- Velocity + 两台 Paper 上的 HuskSync 跨服还原
- HuskSync **3.8.7**、MariaDB、Redis

以上是实际跑过的配置。ItemGuard **并未**在所有插件组合或所有 Paper 版本上完整测试。

普通的 `gradlew.bat clean build` 只编译插件并跑单元测试，**不需要** Docker、Node、HuskSync 或 Velocity。

```bat
gradlew.bat clean test
gradlew.bat integrationTest
gradlew.bat huskSyncIntegrationTest
gradlew.bat upgradeIntegrationTest
gradlew.bat legacyConfigCompatibilityTest
gradlew.bat releaseSmokeTest
```

`upgradeIntegrationTest` 是 **EXACT_ARTIFACT_UPGRADE**。需要原始已发布的 RC1 JAR（`ITEMGUARD_RC1_JAR` 或 `-PRc1Jar=`），校验和 `11581d2fa2a61198a491ef680181f1fc5041e413c54fdbb1f0c54c3d5eeda87b`。它不是 `build` 的一部分。

`legacyConfigCompatibilityTest` 只是 **CONFIG_COMPATIBILITY_UPGRADE**：用 RC1 形态的 YAML fixture 启动 RC2。通过它**不等于**原始 RC1 JAR 已被升级验证。

`integrationTest` 需要 Node.js 和 Paper 1.21.8 JAR。`huskSyncIntegrationTest` 还需要集群依赖。这些运行产生的报告会保留当时实际跑的版本；不会去改写历史 SNAPSHOT 报告。

测试用 Harness 的 HTTP 服务只绑定 `127.0.0.1`，**不包含**在产品 JAR 中。

## 已知限制

1. `/give` 以及未接入、直接调用 `addItem` 的插件可能产生 `UNKNOWN`。请使用 `ItemGuardApi`。
2. 铁砧 / 砂轮 / 酿造没有完整的变换溯源。同材质的元数据变化不按经济获得处理。
3. Paper API 看不见或不可靠的数据，不会假装已经扫描。
4. HuskSync 正式自动化测试仅覆盖 **3.8.7 + Paper 1.21.8**。
5. HS-006 / HS-007 / HS-008 **未**纳入自动化 HuskSync 套件。
6. 玩家当前风险是 **MAX(活跃 incident 分数)**，不是 120 秒窗口内全部信号的全局 SUM。不相关的合法事件不再叠成作弊分；同一 `ITEM_GAIN` 波次内的相关 signal 仍会组合。
7. 没有 SQLite、Web 控制台、日志搜索命令、自动封禁、回滚，也没有 ItemGuard 自有的 Redis。重要事件会异步写入 `plugins/ItemGuard/logs/` 下的本地 JSONL。

## 构建

```bat
gradlew.bat clean build
```

产品 JAR（本仓库开发构建 / Field Test Build）：

```text
build/libs/ItemGuard-1.0.0-RC3-SNAPSHOT.jar
```

已公开发布的 GitHub prerelease 包括不含 hardening 的 `ItemGuard-1.0.0-RC2.jar`，以及 Field Test 预发布 `ItemGuard-1.0.0-RC3-SNAPSHOT.jar`。不要把 SNAPSHOT 当成 RC3 正式发布，也不要复用 `v1.0.0-RC2` 标签。

GitHub Actions 会在 `main` 上编译并上传 JAR；推送 `v*` 标签时会跑单元测试、执行 `packageRelease`，并创建 GitHub Release。官方下载见 [Releases](https://github.com/NPUcraft/ItemGuard/releases)。

本地也可执行 `gradlew.bat packageRelease`，产物在 `build/release/`。

版本只在 `gradle.properties` 定义一次，展开进 `plugin.yml` 的 `${version}`。运行时状态读取 `plugin.getPluginMeta().getVersion()`。

## 许可证

License: TBD
