package kr.berrynames.integrations;

import kr.berrynames.api.BerryNamesApi;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CoreProtectNamesTest {
    final UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    final AtomicReference<List<BerryNamesApi.Identity>> records = new AtomicReference<>(List.of(
            new BerryNamesApi.Identity(first, 1, "FirstAccount", "딸기대장", "베리일"),
            new BerryNamesApi.Identity(second, 2, "SecondAccount", "베리이", "베리이")));
    final BerryNamesApi service = new BerryNamesApi() {
        public Optional<Identity> find(UUID uuid) { return identities().stream().filter(i -> i.uuid().equals(uuid)).findFirst(); }
        public List<Identity> identities() { return records.get(); }
        public List<Identity> findAliases(String query) { return identities().stream().filter(i -> i.displayName().equalsIgnoreCase(query) || i.defaultName().equalsIgnoreCase(query) || ("#" + i.number()).equals(query)).toList(); }
    };
    final CoreProtectNames names = new CoreProtectNames(() -> service);
    @Test void supportsAllUserPrefixesAndNativeActionAliases() {
        for (String action : List.of("lookup", "l", "rollback", "rb", "ro", "restore", "rs", "re"))
            for (String prefix : List.of("u:", "user:", "users:", "p:", "U:")) {
                String[] original = {action, prefix + "딸기대장,베리이", "t:1h", "r:5", "#preview"};
                var result = names.rewrite(original);
                assertArrayEquals(new String[]{action, prefix + "FirstAccount,SecondAccount", "t:1h", "r:5", "#preview"}, result.arguments());
                assertEquals(prefix + "딸기대장,베리이", original[1]);
                assertEquals(2, result.mappings().size());
            }
    }
    @Test void acceptsStableNumberUuidAndWhitespaceSeparatedLists() {
        assertArrayEquals(new String[]{"lookup", "u:", "FirstAccount,", "SecondAccount", "exclude:", "FirstAccount,stone,", "SecondAccount"},
                names.rewrite(new String[]{"lookup", "u:", "#1,", second.toString(), "exclude:", "베리일,stone,", "베리이"}).arguments());
        assertEquals("FirstAccount", names.rewrite(new String[]{"lookup", "베리일"}).arguments()[1]);
    }
    @Test void leavesNonUserValuesAndSpecialActorsUntouched() {
        String[] args = {"lookup", "u:#global,#hopper,#container,Unknown", "i:stone,", "베리일", "t:1h", "f:베리일", "a:chat", "r:5", "#preview"};
        assertArrayEquals(args, names.rewrite(args).arguments());
        for (String action : List.of("inspect", "help", "teleport", "undo", "apply", "page", "near")) {
            String[] command = {action, "베리일"}; assertArrayEquals(command, names.rewrite(command).arguments());
        }
    }
    @Test void nameChangesAreLiveAndDuplicateAccountsFailClosed() {
        records.set(List.of(new BerryNamesApi.Identity(first,1,"FirstAccount","새대장","베리일")));
        assertEquals("u:FirstAccount", names.rewrite(new String[]{"lookup", "u:새대장"}).arguments()[1]);
        assertEquals("u:FirstAccount", names.rewrite(new String[]{"rollback", "u:베리일"}).arguments()[1]);
        records.set(List.of(records.get().getFirst(), new BerryNamesApi.Identity(second,2,"firstaccount","베리이","베리이")));
        assertThrows(IllegalArgumentException.class, () -> names.rewrite(new String[]{"rollback", "u:베리일"}));
        assertThrows(IllegalArgumentException.class, () -> names.rewrite(new String[]{"restore", "u:" + first}));
    }
    @Test void ambiguousAliasAndInvalidAccountAreRejected() {
        records.set(List.of(records.get().getFirst(), new BerryNamesApi.Identity(second,2,"SecondAccount","딸기대장","베리이")));
        assertThrows(IllegalArgumentException.class, () -> names.rewrite(new String[]{"lookup", "u:딸기대장"}));
        records.set(List.of(new BerryNamesApi.Identity(first,1,"bad,account","베리일","베리일")));
        assertThrows(IllegalArgumentException.class, () -> names.rewrite(new String[]{"rollback", "u:베리일"}));
    }
    @Test void exclusionsPreserveMaterialsAndRejectAccountMaterialCollisions() {
        records.set(List.of(new BerryNamesApi.Identity(first,1,"FirstAccount","stone","베리일")));
        assertEquals("e:stone,zombie,minecraft:stone,#minecraft:logs", names.rewrite(new String[]{"lookup", "e:stone,zombie,minecraft:stone,#minecraft:logs"}).arguments()[1]);
        assertEquals("u:FirstAccount", names.rewrite(new String[]{"lookup", "u:stone"}).arguments()[1]);
        records.set(List.of(new BerryNamesApi.Identity(first,1,"Stone","베리일","베리일")));
        assertThrows(IllegalArgumentException.class, () -> names.rewrite(new String[]{"rollback", "e:베리일"}));
    }
    @Test void purgeOnlyTranslatesExcludedUsersAndUnavailableServiceFallsBack() {
        assertArrayEquals(new String[]{"purge", "t:30d", "e:FirstAccount,stone", "u:베리이"},
                names.rewrite(new String[]{"purge", "t:30d", "e:베리일,stone", "u:베리이"}).arguments());
        String[] args = {"lookup", "u:베리일"};
        assertArrayEquals(args, new CoreProtectNames(() -> null).rewrite(args).arguments());
        assertTrue(new CoreProtectNames(() -> null).suggestions(args).isEmpty());
    }
    @Test void completionPreservesCurrentParameterAndCommaPrefix() {
        assertTrue(names.suggestions(new String[]{"lookup", "u:베"}).containsAll(List.of("u:베리일", "u:베리이")));
        assertTrue(names.suggestions(new String[]{"rollback", "u:베리일,베"}).contains("u:베리일,베리이"));
        assertTrue(names.suggestions(new String[]{"restore", "u:", "#"}).contains("#1"));
        assertTrue(names.suggestions(new String[]{"lookup", "e:stone,베"}).contains("e:stone,베리이"));
        for (String token : List.of("i:베", "f:베", "r:베", "t:베", "a:베")) assertTrue(names.suggestions(new String[]{"lookup", token}).isEmpty());
        assertTrue(names.suggestions(new String[]{"lookup", "i:stone,", "베"}).isEmpty());
    }
}
