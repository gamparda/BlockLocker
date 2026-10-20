# CoreProtect integration validation

Use an isolated Paper 26.3 build 201 server with Java 25. Do not run this fixture on
a production server: it places/removes/restores blocks at (20,85,20)/(21,85,20),
renames a fixture identity and shuts the server down.

Install BerryNames 1.3.0, PacketEvents 2.14.0, BerryIntegrations 1.1.0 and the user's
unchanged CoreProtect 24.1. The CoreProtect binary is not redistributed. A fresh local
SQLite database with API enabled is required. No production config, donation key or
database is needed. Optional BlueMap and BlockLocker were present during validation.

Copy `fixture-players.yml` to the test server's BerryNames data folder. Use Java 25's
`javac` on PATH and run `./build.ps1 -ServerRoot <isolated-server>`. Copy the resulting
`core-harness.jar` to that server's plugins directory, then start the server normally.
The harness exits automatically, writing `core-success.ok` or `core-failure.txt`.
Restart without deleting `core-success.ok` to run persistence checks and write
`core-restart.ok`. Retain the first run and restart logs separately.

The public CoreProtect API creates/query-checks placement logs. A fixture subclass
of CoreProtect's Queue invokes its protected login-queue method only to seed UUID
history. Native PluginCommand.execute retains a capturing ConsoleCommandSender proxy;
Paper's global dispatcher normalizes console proxies to the actual console and
therefore cannot capture their output reliably. The fixture Spigot facade captures
both single-component and varargs outputs.

Checks cover native lookup filters, named/namespaced command labels, offline alias
completion, denial permissions, include/exclude lists, live BerryNames renames,
actual native rollback/restore and a second user's unchanged block, original UUID
history, command handler restoration, CoreProtect reload and full-server restart.
The forced disable/re-enable test emits Paper's expected unregistered-classloader
warning; hot swapping is not a supported installation workflow. The BlueMap hook is
disabled in memory for that forced cycle because Paper closes its resource loader.
Normal restarts load all configured hooks. No real Minecraft client was used.
