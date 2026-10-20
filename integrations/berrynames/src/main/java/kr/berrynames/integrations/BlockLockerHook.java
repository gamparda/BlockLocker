package kr.berrynames.integrations;

import kr.berrynames.api.BerryNamesApi;
import nl.rutgerkok.blocklocker.BlockLockerPlugin;
import nl.rutgerkok.blocklocker.PlayerNameProvider;
import java.util.*;
import java.util.function.Supplier;

final class BlockLockerHook implements AutoCloseable {
    private final BlockLockerPlugin target;
    private final PlayerNameProvider previous, installed;

    BlockLockerHook(Object target, Supplier<BerryNamesApi> names) {
        if (!(target instanceof BlockLockerPlugin plugin)) throw new IllegalArgumentException("BlockLocker plugin API is unavailable");
        this.target = plugin;
        previous = plugin.getPlayerNameProvider();
        installed = provider(names, previous);
        plugin.setPlayerNameProvider(installed);
    }
    static PlayerNameProvider provider(Supplier<BerryNamesApi> names, PlayerNameProvider fallback) {
        return new PlayerNameProvider() {
            private Identity identity(BerryNamesApi.Identity entry) {
                return new Identity(entry.uuid(), entry.accountName(), entry.displayName(), List.of(entry.defaultName(), "#" + entry.number()));
            }
            @Override public Optional<Identity> find(UUID uuid) {
                BerryNamesApi api = names.get();
                return api == null ? fallback.find(uuid) : api.find(uuid).map(this::identity).or(() -> fallback.find(uuid));
            }
            @Override public Optional<Identity> resolve(String input) {
                BerryNamesApi api = names.get();
                if (api == null) return fallback.resolve(input);
                try {
                    if (input.length() == 36) return find(UUID.fromString(input));
                } catch (IllegalArgumentException ignored) {}
                List<BerryNamesApi.Identity> matches = api.findAliases(input);
                if (matches.isEmpty()) matches = api.identities().stream().filter(entry -> entry.accountName().equalsIgnoreCase(input)).toList();
                if (matches.size() > 1) throw new IllegalArgumentException("이름이 여러 UUID와 일치합니다. 베리 순번 이름이나 UUID를 지정하세요.");
                return matches.isEmpty() ? fallback.resolve(input) : Optional.of(identity(matches.getFirst()));
            }
            @Override public List<Identity> identities() {
                BerryNamesApi api = names.get();
                if (api == null) return fallback.identities();
                var result = new LinkedHashMap<UUID, Identity>();
                api.identities().forEach(entry -> result.put(entry.uuid(), identity(entry)));
                fallback.identities().forEach(entry -> result.putIfAbsent(entry.uuid(), entry));
                return List.copyOf(result.values());
            }
        };
    }
    @Override public void close() { if (target.getPlayerNameProvider() == installed) target.setPlayerNameProvider(previous); }
}
