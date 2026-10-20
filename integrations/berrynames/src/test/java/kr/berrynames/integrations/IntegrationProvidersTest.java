package kr.berrynames.integrations;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.plugin.PlayerDisplayNameProvider;
import kr.berrynames.api.BerryNamesApi;
import nl.rutgerkok.blocklocker.PlayerNameProvider;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntegrationProvidersTest {
    private final UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    private BerryNamesApi names(AtomicReference<String> name) {
        return new BerryNamesApi() {
            public Optional<Identity> find(UUID uuid) { return identities().stream().filter(entry -> entry.uuid().equals(uuid)).findFirst(); }
            public List<Identity> identities() { return List.of(new Identity(first,1,"ReusedAccount",name.get(),"베리일"), new Identity(second,2,"ReusedAccount","베리이","베리이")); }
            public List<Identity> findAliases(String input) { return identities().stream().filter(entry -> entry.displayName().equals(input) || entry.defaultName().equals(input) || ("#"+entry.number()).equals(input)).toList(); }
        };
    }
    @Test void blockLockerUsesExactUuidAndRejectsAmbiguousAccounts() {
        var api = names(new AtomicReference<>("딸기대장"));
        var provider = BlockLockerHook.provider(() -> api, PlayerNameProvider.NONE);
        for (String alias : List.of("딸기대장", "베리일", "#1", first.toString())) assertEquals(first, provider.resolve(alias).orElseThrow().uuid());
        assertThrows(IllegalArgumentException.class, () -> provider.resolve("ReusedAccount"));
        assertTrue(provider.resolve("딸기").isEmpty()); assertEquals(2, provider.identities().size());
    }
    @Test void blockLockerSeesNameChangesAndFallsBackWhenServiceDisappears() {
        var current = new AtomicReference<>("베리일");
        var api = new AtomicReference<>(names(current));
        var provider = BlockLockerHook.provider(api::get, PlayerNameProvider.NONE);
        current.set("새대장"); assertEquals("새대장", provider.find(first).orElseThrow().displayName());
        assertEquals(first, provider.resolve("베리일").orElseThrow().uuid());
        api.set(null); assertTrue(provider.find(first).isEmpty()); assertTrue(provider.identities().isEmpty());
    }
    @Test void blueMapUsesCurrentNameAndRestoresPreviousProvider() {
        var current = new AtomicReference<>("베리일");
        var service = new AtomicReference<>(names(current));
        var plugin = mock(de.bluecolored.bluemap.api.plugin.Plugin.class);
        var installed = new AtomicReference<PlayerDisplayNameProvider>(uuid -> "original");
        PlayerDisplayNameProvider original = installed.get();
        when(plugin.getPlayerDisplayNameProvider()).thenAnswer(invocation -> installed.get());
        doAnswer(invocation -> { installed.set(invocation.getArgument(0)); return null; }).when(plugin).setPlayerDisplayNameProvider(any());
        var api = mock(BlueMapAPI.class); when(api.getPlugin()).thenReturn(plugin);
        var hook = new BlueMapHook(service::get);
        hook.attach(api);
        assertEquals("베리일", installed.get().get(first));
        current.set("새대장"); assertEquals("새대장", installed.get().get(first));
        assertEquals("original", installed.get().get(UUID.randomUUID()));
        service.set(null); assertEquals("original", installed.get().get(first));
        hook.close(); assertSame(original, installed.get());
    }
    @Test void blueMapReloadReattachesAndDoesNotOverwriteAnotherAddonsProvider() {
        var plugin = mock(de.bluecolored.bluemap.api.plugin.Plugin.class);
        var state = new AtomicReference<PlayerDisplayNameProvider>(uuid -> "original");
        when(plugin.getPlayerDisplayNameProvider()).thenAnswer(invocation -> state.get());
        doAnswer(invocation -> { state.set(invocation.getArgument(0)); return null; }).when(plugin).setPlayerDisplayNameProvider(any());
        var api = mock(BlueMapAPI.class); when(api.getPlugin()).thenReturn(plugin);
        var hook = new BlueMapHook(() -> names(new AtomicReference<>("베리일")));
        hook.attach(api); hook.detach(api); assertEquals("original", state.get().get(first));
        hook.attach(api); assertEquals("베리일", state.get().get(first));
        PlayerDisplayNameProvider another = uuid -> "other-addon"; state.set(another);
        hook.close(); assertSame(another, state.get());
    }
}
