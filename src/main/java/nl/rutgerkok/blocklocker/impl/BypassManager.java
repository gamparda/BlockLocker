package nl.rutgerkok.blocklocker.impl;
import nl.rutgerkok.blocklocker.impl.recovery.RecoveryYaml;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Stores players that are allowed to bypass protections without being operators.
 */
public final class BypassManager {

    private final File file;
    private final Map<UUID, String> playerNamesByUuid = new LinkedHashMap<>();
    private final Set<String> legacyPlayerNames = new LinkedHashSet<>();

    BypassManager(BlockLockerPluginImpl plugin) {
        this.file = new File(plugin.getDataFolder(), "bypass.yml");
        load(plugin);
    }

    public boolean add(String playerName, BlockLockerPluginImpl plugin) {
        var identity = plugin.getPlayerNameProvider().resolve(playerName);
        if (identity.isPresent()) {
            var target = identity.get();
            legacyPlayerNames.remove(normalize(target.accountName()));
            return !target.accountName().equals(playerNamesByUuid.put(target.uuid(), target.accountName()));
        }
        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerName);
        UUID uniqueId = offlinePlayer.getUniqueId();
        String lastKnownName = offlinePlayer.getName() == null || offlinePlayer.getName().isBlank()
                ? playerName
                : offlinePlayer.getName();
        legacyPlayerNames.remove(normalize(playerName));
        return !lastKnownName.equals(playerNamesByUuid.put(uniqueId, lastKnownName));
    }

    public boolean canBypass(Player player) {
        return playerNamesByUuid.containsKey(player.getUniqueId()) || legacyPlayerNames.contains(normalize(player.getName()));
    }

    public List<String> getPlayerNames() {
        List<String> names = new ArrayList<>(playerNamesByUuid.values());
        names.addAll(legacyPlayerNames);
        return names;
    }

    public List<String> getPlayerNames(BlockLockerPluginImpl plugin) {
        List<String> names = new ArrayList<>();
        playerNamesByUuid.forEach((uuid, account) -> names.add(plugin.getPlayerNameProvider().find(uuid)
                .map(nl.rutgerkok.blocklocker.PlayerNameProvider.Identity::displayName).orElse(account)));
        names.addAll(legacyPlayerNames);
        return names;
    }

    public boolean remove(String playerName, BlockLockerPluginImpl plugin) {
        var identity = plugin.getPlayerNameProvider().resolve(playerName);
        if (identity.isPresent()) {
            var target = identity.get();
            boolean changed = legacyPlayerNames.remove(normalize(target.accountName()));
            return (playerNamesByUuid.remove(target.uuid()) != null) || changed;
        }
        boolean changed = legacyPlayerNames.remove(normalize(playerName));

        Player onlinePlayer = plugin.getServer().getPlayerExact(playerName);
        if (onlinePlayer != null) {
            changed |= playerNamesByUuid.remove(onlinePlayer.getUniqueId()) != null;
        }

        String normalized = normalize(playerName);
        UUID uuidToRemove = null;
        for (Map.Entry<UUID, String> entry : playerNamesByUuid.entrySet()) {
            if (normalize(entry.getValue()).equals(normalized)) {
                uuidToRemove = entry.getKey();
                break;
            }
        }
        if (uuidToRemove != null) {
            playerNamesByUuid.remove(uuidToRemove);
            changed = true;
        }
        return changed;
    }

    public void save(BlockLockerPluginImpl plugin) {
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, String>> players = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : playerNamesByUuid.entrySet()) {
            Map<String, String> player = new LinkedHashMap<>();
            player.put("uuid", entry.getKey().toString());
            player.put("lastKnownName", entry.getValue());
            players.add(player);
        }
        config.set("players", players);
        if (!legacyPlayerNames.isEmpty()) {
            config.set("legacyPlayerNames", new ArrayList<>(legacyPlayerNames));
        }
        try {
            RecoveryYaml.write(file.toPath(),config.saveToString(),plugin.getLogger()::warning);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save bypass.yml", e);
        }
    }

    private void load(BlockLockerPluginImpl plugin) {
        playerNamesByUuid.clear();
        legacyPlayerNames.clear();
        YamlConfiguration config;
        try{config=RecoveryYaml.data(file.toPath(),BypassManager::normalize,plugin.getLogger()::warning);}
        catch(IOException e){throw new IllegalStateException("bypass.yml 복구 실패",e);}
        List<?> players = config.getList("players", List.of());
        for (Object playerEntry : players) {
            if (playerEntry instanceof String playerName) {
                // Old format: players: [name, name]
                if (!playerName.isBlank()) {
                    legacyPlayerNames.add(normalize(playerName));
                }
                continue;
            }
            if (!(playerEntry instanceof Map<?, ?> playerMap)) {
                continue;
            }
            Object uuidObject = playerMap.get("uuid");
            Object nameObject = playerMap.get("lastKnownName");
            if (!(uuidObject instanceof String uuidString) || !(nameObject instanceof String playerName)) {
                continue;
            }
            try {
                playerNamesByUuid.put(UUID.fromString(uuidString), playerName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid UUID in bypass.yml: " + uuidString);
            }
        }

        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection != null) {
            for (String key : playersSection.getKeys(false)) {
                String uuidString = playersSection.getString(key + ".uuid");
                String playerName = playersSection.getString(key + ".lastKnownName");
                if (uuidString == null || playerName == null) {
                    continue;
                }
                try {
                    playerNamesByUuid.put(UUID.fromString(uuidString), playerName);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Ignoring invalid UUID in bypass.yml: " + uuidString);
                }
            }
        }

        for (String playerName : config.getStringList("legacyPlayerNames")) {
            if (!playerName.isBlank()) {
                legacyPlayerNames.add(normalize(playerName));
            }
        }
    }

    private String normalize(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }
    private static boolean normalize(YamlConfiguration yaml) {
        String before=yaml.saveToString();Object raw=yaml.get("players");
        if(raw!=null&&!(raw instanceof List<?>)&&!yaml.isConfigurationSection("players"))yaml.set("players",List.of());
        if(raw instanceof List<?> list) {
            var valid=new ArrayList<Object>();
            for(Object item:list) {
                if(item instanceof String name&&!name.isBlank())valid.add(name);
                else if(item instanceof Map<?,?> map&&map.get("uuid") instanceof String uuid&&RecoveryYaml.uuid(uuid)&&map.get("lastKnownName") instanceof String)valid.add(map);
            }
            yaml.set("players",valid);
        }
        if(yaml.get("legacyPlayerNames")!=null&&!(yaml.get("legacyPlayerNames") instanceof List<?>))yaml.set("legacyPlayerNames",List.of());
        return !before.equals(yaml.saveToString());
    }

}
