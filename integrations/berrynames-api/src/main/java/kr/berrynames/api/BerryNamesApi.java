package kr.berrynames.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only, thread-safe identities. Obtain the current provider through Bukkit ServicesManager. */
public interface BerryNamesApi {
    record Identity(UUID uuid, long number, String accountName, String displayName, String defaultName) {}

    Optional<Identity> find(UUID uuid);
    List<Identity> identities();

    /** Exact current display name, stable default name or #number; never a historical account name. */
    List<Identity> findAliases(String query);
}
