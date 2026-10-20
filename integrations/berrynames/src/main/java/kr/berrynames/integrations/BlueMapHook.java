package kr.berrynames.integrations;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.plugin.PlayerDisplayNameProvider;
import kr.berrynames.api.BerryNamesApi;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class BlueMapHook implements AutoCloseable {
    private final Supplier<BerryNamesApi> names;
    private final Consumer<BlueMapAPI> enabled = this::attach;
    private final Consumer<BlueMapAPI> disabled = this::detach;
    private de.bluecolored.bluemap.api.plugin.Plugin target;
    private PlayerDisplayNameProvider previous, installed;
    private boolean closed;

    BlueMapHook(Supplier<BerryNamesApi> names) {
        this.names = names;
        BlueMapAPI.onEnable(enabled);
        BlueMapAPI.onDisable(disabled);
    }
    synchronized void attach(BlueMapAPI api) {
        if (closed || target == api.getPlugin()) return;
        restore();
        var plugin = api.getPlugin();
        var fallback = plugin.getPlayerDisplayNameProvider();
        PlayerDisplayNameProvider provider = uuid -> {
            BerryNamesApi service = names.get();
            return service == null ? fallback.get(uuid) : service.find(uuid).map(BerryNamesApi.Identity::displayName).orElseGet(() -> fallback.get(uuid));
        };
        plugin.setPlayerDisplayNameProvider(provider);
        target = plugin; previous = fallback; installed = provider;
    }
    synchronized void detach(BlueMapAPI api) { if (target == api.getPlugin()) restore(); }
    private void restore() {
        if (target != null && target.getPlayerDisplayNameProvider() == installed) target.setPlayerDisplayNameProvider(previous);
        target = null; previous = null; installed = null;
    }
    @Override public void close() {
        // Unregister before taking our lock; BlueMap invokes callbacks under its own lock.
        BlueMapAPI.unregisterListener(enabled); BlueMapAPI.unregisterListener(disabled);
        synchronized (this) { closed = true; restore(); }
    }
}
