# BerryNames integration

`berrynames/` builds **BerryIntegrations 1.0.0**, an optional Paper plugin integrating
BerryNames 1.3.0+ with BlueMap 5.29 and this BlockLocker fork.

The addon and the BerryNames API contract in `berrynames-api/` are GPLv3; each module
contains its license. The main BlockLocker plugin retains its MIT license and has
no dependency on BerryNames or BlueMap. The contract matches BerryNames 1.3.0's
`kr.berrynames.api.BerryNamesApi`. It is a provided compile-time dependency and is
not included in the addon JAR. At runtime, BerryNames supplies the API class and service.

## Build

Use Java 25 and Maven. From the repository root:

```sh
mvn -B -ntp install
mvn -B -ntp -f integrations/pom.xml verify
```

Install these plugin JARs, retaining their existing data folders:

- `target/blocklocker-1.15.1-berry.1.jar`
- `integrations/berrynames/target/BerryIntegrations-1.0.0.jar`
- BerryNames 1.3.0+ and its PacketEvents dependency
- BlueMap 5.29 when using the map integration

**Do not install `BerryNames-api-1.3.0.jar` in plugins.** It is only the build-time contract.
BlueMap and BlockLocker are optional for the addon; either can be used independently.
`plugins/BerryIntegrations/config.yml` can disable either hook before a server restart.

## Behavior

- BlueMap uses its public `PlayerDisplayNameProvider`, including native player markers
  and the live player list. UUIDs, skins, positions, and all existing hide rules remain
  under BlueMap's control. BerryNames renames appear on the next live-data update.
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

The addon uses public APIs. It does not patch BlueMap, change UUIDs, write BerryNames
data, or modify CoreProtect.

## Validation

Java 25: 19 BlockLocker tests and 4 addon tests passed. Paper 26.3 build 201 with
BlueMap 5.29, BerryNames 1.3.0, PacketEvents 2.14.0 and this fork passed 40 integration
checks and 9 full-restart checks. Tests used actual Paper blocks, signs, persistent
data, commands, UUID protections, BlueMap's live transformer/JSON and test Player
proxies. No real Minecraft client manual interaction was performed.
