package nl.rutgerkok.blocklocker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Optional nickname integration. UUIDs remain the authority for saved protections. */
public interface PlayerNameProvider {
    record Identity(UUID uuid, String accountName, String displayName, List<String> aliases) {
        public Identity { aliases = List.copyOf(aliases); }
    }
    PlayerNameProvider NONE = new PlayerNameProvider() {};
    default Optional<Identity> find(UUID uuid) { return Optional.empty(); }
    default Optional<Identity> resolve(String input) { return Optional.empty(); }
    default List<Identity> identities() { return List.of(); }
}
