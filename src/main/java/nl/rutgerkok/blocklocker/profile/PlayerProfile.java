package nl.rutgerkok.blocklocker.profile;

import java.util.Optional;
import java.util.UUID;

public interface PlayerProfile extends Profile {
    /** The real account name, used only as a fallback for legacy profiles without UUIDs. */
    default String getAccountName() { return getDisplayName(); }

    /**
     * Gets the unique id of this player, if any.
     *
     * @return The unique id.
     */
    Optional<UUID> getUniqueId();

}
