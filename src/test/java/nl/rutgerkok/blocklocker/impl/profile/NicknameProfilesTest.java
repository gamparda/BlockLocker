package nl.rutgerkok.blocklocker.impl.profile;

import nl.rutgerkok.blocklocker.PlayerNameProvider;
import nl.rutgerkok.blocklocker.impl.JsonSecretSignEntry;
import nl.rutgerkok.blocklocker.impl.ProtectionAccessList;
import nl.rutgerkok.blocklocker.group.CombinedGroupSystem;
import nl.rutgerkok.blocklocker.profile.PlayerProfile;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class NicknameProfilesTest {
    private final UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    private final AtomicReference<String> display = new AtomicReference<>("베리일");
    private final PlayerNameProvider names = new PlayerNameProvider() {
        public Optional<Identity> find(UUID uuid) {
            return uuid.equals(first) ? Optional.of(new Identity(first, "Alpha", display.get(), List.of("베리일", "#1"))) : Optional.empty();
        }
        public Optional<Identity> resolve(String input) {
            return List.of("베리일", "#1", display.get()).contains(input) ? find(first) : Optional.empty();
        }
    };
    private ProfileFactoryImpl factory() { return new ProfileFactoryImpl(new CombinedGroupSystem(), new NullTranslator(), () -> names); }
    @Test void aliasesBecomeUuidProfilesAndRetainRealAccount() {
        for (String input : List.of("베리일", "#1")) {
            PlayerProfile profile = factory().fromNameAndUniqueId(input, Optional.empty());
            assertEquals(first, profile.getUniqueId().orElseThrow());
            assertEquals("베리일", profile.getDisplayName()); assertEquals("Alpha", profile.getAccountName());
        }
    }
    @Test void savedVisibleTextIsNotResolvedAgainAfterNameChange() {
        var factory = factory();
        var saved = new JsonSecretSignEntry(new JsonObject());
        factory.fromNameAndUniqueId("Alpha", Optional.of(first)).getSaveObject(saved);
        assertEquals("베리일", saved.getString("n").orElseThrow());
        assertEquals("Alpha", saved.getString("a").orElseThrow());
        display.set("딸기대장");
        PlayerProfile loaded = (PlayerProfile)factory.fromSavedObject(saved).orElseThrow();
        assertEquals("베리일", loaded.getDisplayName());
        assertEquals(first, loaded.getUniqueId().orElseThrow());
        PlayerProfile refreshed = factory.fromNameAndUniqueId(loaded.getAccountName(), loaded.getUniqueId());
        assertEquals("딸기대장", refreshed.getDisplayName());
        assertEquals(loaded, refreshed);
        refreshed.getSaveObject(saved);
        assertEquals(first, saved.getUniqueId("u").orElseThrow());
    }
    @Test void uuidCannotBeReboundByAChangedNameOrDuplicateDisplayName() {
        var factory = factory();
        var one = factory.fromNameAndUniqueId("Alpha", Optional.of(first));
        var other = factory.fromNameAndUniqueId("베리일", Optional.of(second));
        assertEquals(second, other.getUniqueId().orElseThrow());
        assertFalse(one.includes(other)); assertFalse(other.includes(one));
        assertFalse(ProtectionAccessList.isSameProfile(one, other));
    }
    @Test void legacyUnknownUuidUsesRealAccountForAuthorization() {
        var factory = factory();
        var legacy = factory.fromNameAndUniqueId("Alpha", Optional.empty());
        var renamed = factory.fromNameAndUniqueId("Alpha", Optional.of(first));
        assertTrue(legacy.includes(renamed));
        var unrelated = factory.fromNameAndUniqueId("Alpha", Optional.of(second));
        assertFalse(renamed.includes(unrelated));
    }
    @Test void groupAndSpecialTagsAreNotInterpretedAsNicknames() {
        var factory = factory();
        for (String value : List.of("[Everyone]", "[Redstone]", "[Timer:3]", "[MyGroup]", "+MyGroup+"))
            assertFalse(factory.fromDisplayText(value) instanceof PlayerProfile);
        assertEquals("", factory.fromDisplayText("").getDisplayName());
    }
    @Test void oldSavedProfilesAndProviderRemovalKeepUuid() {
        var data = new JsonSecretSignEntry(new JsonObject());
        data.setString("n", "Alpha"); data.setUniqueId("u", first);
        PlayerProfile legacy = (PlayerProfile)factory().fromSavedObject(data).orElseThrow();
        assertEquals("Alpha", legacy.getAccountName());
        var plain = new ProfileFactoryImpl(new CombinedGroupSystem(), new NullTranslator());
        var real = plain.fromNameAndUniqueId(legacy.getAccountName(), legacy.getUniqueId());
        assertEquals("Alpha", real.getDisplayName()); assertEquals(first, real.getUniqueId().orElseThrow());
    }
}
