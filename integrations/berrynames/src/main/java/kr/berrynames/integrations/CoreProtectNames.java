package kr.berrynames.integrations;

import kr.berrynames.api.BerryNamesApi;
import java.util.*;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/** Translates only native CoreProtect user arguments; never changes stored log identities. */
final class CoreProtectNames {
    record Mapping(UUID uuid, String displayName, String accountName) { }
    record Result(String[] arguments, List<Mapping> mappings) { }
    private enum Mode { NONE, USER, EXCLUDE, OTHER }
    private final Supplier<BerryNamesApi> names;

    CoreProtectNames(Supplier<BerryNamesApi> names) { this.names = names; }

    static String permission(String action) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "lookup", "l" -> "coreprotect.lookup";
            case "rollback", "rb", "ro" -> "coreprotect.rollback";
            case "restore", "rs", "re" -> "coreprotect.restore";
            case "purge" -> "coreprotect.purge";
            default -> null;
        };
    }

    Result rewrite(String[] arguments) {
        var result = arguments.clone();
        if (arguments.length == 0 || permission(arguments[0]) == null) return new Result(result, List.of());
        BerryNamesApi service = names.get();
        if (service == null) return new Result(result, List.of());
        boolean purge = arguments[0].equalsIgnoreCase("purge");
        var mappings = new LinkedHashMap<UUID, Mapping>();
        Mode mode = Mode.NONE;
        for (int i = 1; i < arguments.length; i++) {
            String token = arguments[i];
            if (mode == Mode.USER || mode == Mode.EXCLUDE) {
                result[i] = list(token, service, mappings, mode == Mode.EXCLUDE);
                if (!token.endsWith(",") && !token.isEmpty()) mode = Mode.NONE;
                continue;
            }
            if (mode == Mode.OTHER) {
                if (!token.endsWith(",")) mode = Mode.NONE;
                continue;
            }
            int colon = token.indexOf(':');
            String key = colon < 0 ? "" : token.substring(0, colon).toLowerCase(Locale.ROOT);
            Mode selected = switch (key) {
                case "u", "user", "users", "p" -> purge ? Mode.OTHER : Mode.USER;
                case "e", "exclude" -> Mode.EXCLUDE;
                default -> Mode.NONE;
            };
            if (selected == Mode.USER || selected == Mode.EXCLUDE) {
                String body = token.substring(colon + 1);
                result[i] = token.substring(0, colon + 1) + list(body, service, mappings, selected == Mode.EXCLUDE);
                if (body.isEmpty() || body.endsWith(",")) mode = selected;
            } else if (colon >= 0 || token.endsWith(",")) {
                if (token.endsWith(":") || token.endsWith(",")) mode = Mode.OTHER;
            } else if (!purge) {
                result[i] = user(token, service, mappings);
            }
        }
        return new Result(result, List.copyOf(mappings.values()));
    }

    private String list(String value, BerryNamesApi service, Map<UUID, Mapping> mappings, boolean excluded) {
        String[] parts = value.split(",", -1);
        for (int i = 0; i < parts.length; i++) {
            if (excluded && materialOrEntity(parts[i])) continue;
            String translated = user(parts[i], service, mappings);
            if (excluded && !translated.equals(parts[i]) && materialOrEntity(translated))
                throw new IllegalArgumentException("'" + parts[i] + "'의 실제 계정명이 블록/엔티티 이름과 겹쳐 CoreProtect 제외 대상으로 구분할 수 없습니다.");
            parts[i] = translated;
        }
        return String.join(",", parts);
    }

    private static boolean materialOrEntity(String value) {
        String key = value.toUpperCase(Locale.ROOT).replaceFirst("^MINECRAFT:", "");
        try { Material.valueOf(key); return true; } catch (IllegalArgumentException ignored) { }
        try { EntityType.valueOf(key); return true; } catch (IllegalArgumentException ignored) { }
        return value.startsWith("#") && !value.matches("#[0-9]+");
    }

    private String user(String value, BerryNamesApi service, Map<UUID, Mapping> mappings) {
        if (value.isEmpty() || value.equalsIgnoreCase("#global") || value.equalsIgnoreCase("#hopper")
                || value.equalsIgnoreCase("#container")) return value;
        List<BerryNamesApi.Identity> matches;
        try {
            matches = value.length() == 36 ? service.find(UUID.fromString(value)).stream().toList() : service.findAliases(value);
        } catch (IllegalArgumentException e) { matches = service.findAliases(value); }
        if (matches.isEmpty()) return value;
        if (matches.size() != 1) throw new IllegalArgumentException("'" + value + "' 이름이 여러 사람과 일치합니다. 순번 이름 또는 #번호를 사용하세요.");
        var identity = matches.getFirst();
        String account = identity.accountName();
        if (account == null || !account.matches("[A-Za-z0-9_]{1,16}"))
            throw new IllegalArgumentException("'" + value + "'의 실제 계정명을 확인할 수 없습니다.");
        if (service.identities().stream().anyMatch(other -> !other.uuid().equals(identity.uuid()) && account.equalsIgnoreCase(other.accountName())))
            throw new IllegalArgumentException("'" + value + "'의 실제 계정명이 다른 UUID와 중복됩니다. CoreProtect의 계정명 대상 조회로는 안전하게 구분할 수 없습니다.");
        mappings.putIfAbsent(identity.uuid(), new Mapping(identity.uuid(), identity.displayName(), account));
        return account;
    }

    List<String> suggestions(String[] arguments) {
        if (arguments.length < 2 || permission(arguments[0]) == null) return List.of();
        BerryNamesApi service = names.get();
        if (service == null) return List.of();
        // Probe only the cursor's value, preserving previous values and the current parameter prefix.
        String marker = "\u0001BERRY_CURSOR\u0001";
        String current = arguments[arguments.length - 1];
        int start = Math.max(current.lastIndexOf(','), current.indexOf(':')) + 1;
        String prefix = current.substring(0, start), query = current.substring(start);
        var cursor = arguments.clone(); cursor[cursor.length - 1] = prefix + marker;
        var probe = new BerryNamesApi() {
            public Optional<Identity> find(UUID uuid) { return Optional.empty(); }
            public List<Identity> identities() { return List.of(); }
            public List<Identity> findAliases(String input) {
                if (input.equals(marker)) throw new CursorFound();
                return List.of();
            }
        };
        try { new CoreProtectNames(() -> probe).rewrite(cursor); return List.of(); }
        catch (CursorFound found) { /* cursor is in a user position */ }
        var result = new LinkedHashSet<String>();
        for (var identity : service.identities()) {
            for (String name : List.of(identity.displayName(), identity.defaultName(), "#" + identity.number())) {
                if (!name.toLowerCase(Locale.ROOT).startsWith(query.toLowerCase(Locale.ROOT))) continue;
                try {
                    if (!user(name, service, new LinkedHashMap<>()).equals(name) || name.equals(identity.accountName())) result.add(prefix + name);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return List.copyOf(result);
    }

    private static final class CursorFound extends RuntimeException { }
}
