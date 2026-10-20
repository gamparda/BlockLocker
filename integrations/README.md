# BerryNames integration

`berrynames/` builds **BerryIntegrations 1.2.0**, an optional Paper plugin integrating
BerryNames 1.3.0+ with BlueMap 5.29, this BlockLocker fork, and CoreProtect 24.1.

The addon and the BerryNames API contract in `berrynames-api/` are GPLv3; each module
contains its license. The main BlockLocker plugin retains its MIT license and has
no dependency on BerryNames or BlueMap. The contract matches BerryNames 1.3.0's
`kr.berrynames.api.BerryNamesApi` and `AliasAwareCommands`. It is a provided compile-time dependency and is
not included in the addon JAR. At runtime, BerryNames supplies the API class and service.

## Build

Use Java 25 and Maven. From the repository root:

```sh
mvn -B -ntp install
mvn -B -ntp -f integrations/pom.xml verify
```

Install these plugin JARs, retaining their existing data folders:

- `target/blocklocker-1.15.1-berry.1.jar`
- `integrations/berrynames/target/BerryIntegrations-1.2.0.jar`
- BerryNames 1.3.0+ and its PacketEvents dependency
- BlueMap 5.29 when using the map integration
- The existing CoreProtect 24.1 JAR when using its command integration

**Do not install `BerryNames-api-1.3.0.jar` in plugins.** It is only the build-time contract.
BlueMap, BlockLocker and CoreProtect are optional; each can be used independently.
`plugins/BerryIntegrations/config.yml` can disable each hook before a server restart.

## Behavior

- BlueMap uses its public `PlayerDisplayNameProvider`, including native player markers
  and the live player list. UUIDs, skins, positions, and all existing hide rules remain
  under BlueMap's control. BerryNames renames appear on the next live-data update.
- The addon writes `assets/berrynames/player-names-v1.css` inside BlueMap's configured
  web root and registers it with `WebApp.registerStyle`. This keeps Hangul player names
  on one horizontal line. The stylesheet only targets player-marker labels; live names
  stay plain text. Existing custom styles and BlueMap configuration are retained.
  The file is refreshed after BlueMap API reloads. A write failure is logged and does
  not disable name resolution. For a separately hosted web root, publish this generated
  file together with BlueMap's generated `settings.json`.
- `/bl trust`, `untrust`, `transfer`, and `bypass add/remove` resolve exact Berry display
  names, stable default names, `#number`, known real account names, or known UUIDs.
  Ambiguous stored account names require a Berry name or UUID.
- Sign creation and legacy text conversion use the same profile factory. Groups,
  `[Everyone]`, `[Redstone]`, `[Golem]`, and timers retain their existing syntax.
- Saved profiles preserve their UUID and their last visible `n` value. A new `a`
  field keeps the actual account name for fallback. Reading saved data never resolves
  the visible name into another identity. The normal updater refreshes a sign's name
  on BlockLocker interaction or `/bl list`, including offline owners.
- Unloaded chunks are not scanned or forced to load. Existing signs are updated when
  BlockLocker next reads and updates their protection.
- Existing settings GUI, trust-list compaction, bypass permissions and ownership checks
  are retained. An addon shutdown restores previous providers if it still owns them.
- The core provider reference survives `/bl reload`. BlueMap callbacks reconnect after
  BlueMap API reloads. Plugin hot swapping is not an installation workflow.

Names and commands use public APIs. CoreProtect 24.1's output requires a checked
runtime bridge to its internal renderer, as described below. The addon does not patch
BlueMap, change UUIDs, write BerryNames data, or modify CoreProtect's JAR or database.

To update from 1.0.x, replace only the BerryIntegrations JAR while the server is stopped,
restart it, then hard-refresh the map page (Ctrl+F5). No map rerender is needed.
The generated stylesheet can remain when disabling the addon; it only prevents
player-name wrapping. BlueMap's API has no style-unregister method.

## CoreProtect commands

The addon wraps the Bukkit executors/completers of CoreProtect's own `co`, `core` and
`coreprotect` commands. Namespaced labels work through the same PluginCommands.
No CoreProtect classes are linked or bundled; the proprietary/local plugin remains
unchanged. Execution uses the original sender, native permissions, native filtering,
preview, pagination, rollback/restore processing and defaults.

Current display names, stable Berry names, `#number`, and known UUIDs are translated
to the actual account name for `lookup/l`, `rollback/rb/ro`, and `restore/rs/re`.
User prefixes `u:`, `user:`, `users:`, `p:`, comma lists, continued lists and native bare
user arguments are supported. `e:`/`exclude:` user exclusions are translated too,
including exclusions in `purge`. Native material/entity exclusions and other filters
remain intact. Native tab suggestions are retained and Berry names are added only in
user positions with the corresponding command permission.

```text
/co lookup u:베리일 t:1h r:10
/co rollback u:베리일 t:1h r:10 #preview
/co restore u:베리일 t:1h r:10
/co lookup u:베리일,베리이 e:베리이 t:1h r:10
```

The native command format targets account names. Duplicate stored account names or
ambiguous aliases are rejected instead of choosing a UUID arbitrarily. An exclusion
is rejected if its translated account collides with a native material/entity name.
Renames are read live from BerryNames; the addon never rewrites existing CoreProtect
records. Existing native account arguments continue to work.

**Inspection and lookup output:** native log rows now show the current Berry name.
Hovering the actor reveals the real account, original UUID and Berry number. Native
material/entity fields use Minecraft translation components, so a Korean client sees
`grass_block` as `잔디 블록`. The client's chosen language determines material names.
Unresolved/ambiguous actors retain their original account text. Chat/sign bodies,
historical username values, click commands and item tooltips are preserved.

The output bridge verifies CoreProtect **24.1** and its public `SpigotInterface`
signature before wrapping the internal `SpigotAdapter.ADAPTER` field through reflection.
It captures only timestamped log-row output from that renderer and retains CoreProtect's
own component generation. No global chat rewriting or database/cache substitution is
used. A renderer reset is reattached within one second. Shutdown restores the prior
adapter only if still owned by this addon. Unsupported versions leave command aliases
working and log a warning; output compatibility is not promised for other versions.
The optional `베리일 → ActualAccount` command mapping line remains available.

```yaml
coreprotect:
  enabled: true
  show-mapping: true
  display-names: true
  translate-materials: true
```

These defaults apply when an existing addon config lacks this section. Set
`show-mapping: false` to suppress the mapping line. Disable/re-enable restores command
handlers only when the addon still owns them; normal installation requires a full
server restart. CoreProtect's own `/co reload` retains the wrapper.

## Validation

Java 25: 25 addon tests passed for 1.2.0, covering live inspection names, UUID hover,
legacy Bungee text nodes, rollback formatting, native click/hover preservation,
unmodified chat/history bodies, material translation and ambiguous actors. On Paper
201 / CoreProtect 24.1, the real native BlockInspector and command lookup output
passed 23 checks, including alias changes, native reload, renderer reset, original
UUID/log identities and unchanged blocks. Full restart passed 5 checks. Captured
components resolved through the installed Minecraft Korean language data display
`감파르다이(가) 잔디 블록을(를) 설치함`. These are actual native output components;
no manual Minecraft client session was used. See DisplayHarness.java and
build-display.ps1 in the native validation fixture.

Java 25: 17 addon tests passed for 1.1.0. New cases cover native user grammar,
unchanged non-user filters, UUIDs/renames, duplicate identities, material collisions,
permission delegation, completion and handler restoration. Paper 26.3 build 201
tests use the user's unchanged CoreProtect 24.1 JAR (API v12), a fresh local SQLite
database, native Bukkit PluginCommands, original CoreProtect processing and actual
Paper world blocks. Test senders are capturing proxies, not real client connections.
The combined server run passed 34 checks and the full-restart run passed 8 checks.
The native test fixture and setup instructions are in
`berrynames/validation/coreprotect/`; obtain CoreProtect separately to run it.

Java 25: 5 addon tests passed for 1.0.1, including stylesheet registration and a failed
web-root write retaining name resolution. On Paper 26.3 build 201 / BlueMap 5.29,
the generated style and settings entry were served successfully. Chromium 151 tests
using BlueMap's actual PlayerMarker constructor reproduced `감파르다` as 4 lines without
the style, then verified 7 Korean/Latin names on one line at desktop and mobile widths.
Native distance hiding also passed. Reproduce with Playwright and Chromium installed:

```sh
node integrations/berrynames/validation/player-name-layout.cjs http://127.0.0.1:18101/ layout-results
```

The unchanged BlockLocker core previously passed 19 tests. The 1.0.0 baseline on
Paper 26.3 build 201 with
BlueMap 5.29, BerryNames 1.3.0, PacketEvents 2.14.0 and this fork passed 40 integration
checks and 9 full-restart checks. Tests used actual Paper blocks, signs, persistent
data, commands, UUID protections, BlueMap's live transformer/JSON and test Player
proxies. No real Minecraft client manual interaction was performed.
