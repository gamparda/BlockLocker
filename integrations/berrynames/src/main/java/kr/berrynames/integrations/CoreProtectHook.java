package kr.berrynames.integrations;

import kr.berrynames.api.AliasAwareCommands;
import kr.berrynames.api.BerryNamesApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Delegates execution to CoreProtect's own executor with the original sender and permissions. */
final class CoreProtectHook implements AliasAwareCommands, AutoCloseable {
    private final List<Binding> bindings = new ArrayList<>();

    CoreProtectHook(JavaPlugin coreProtect, Supplier<BerryNamesApi> names, boolean showMapping) {
        var directory = new CoreProtectNames(names);
        for (String label : List.of("co", "core", "coreprotect")) {
            PluginCommand command = coreProtect.getCommand(label);
            if (command == null || command.getPlugin() != coreProtect) continue;
            bindings.add(new Binding(command, directory, showMapping));
        }
        if (bindings.isEmpty()) throw new IllegalArgumentException("CoreProtect commands are unavailable");
    }

    @Override public boolean handles(Command command) { return bindings.stream().anyMatch(binding -> binding.command == command); }
    @Override public void close() { bindings.forEach(Binding::restore); bindings.clear(); }

    static final class Binding implements CommandExecutor, TabCompleter {
        private final PluginCommand command;
        private final CommandExecutor previous;
        private final TabCompleter completer;
        private final CoreProtectNames names;
        private final boolean showMapping;

        Binding(PluginCommand command, CoreProtectNames names, boolean showMapping) {
            this.command = command; this.names = names; this.showMapping = showMapping;
            previous = command.getExecutor(); completer = command.getTabCompleter();
            command.setExecutor(this); command.setTabCompleter(this);
        }
        private boolean allowed(CommandSender sender, String[] arguments) {
            String permission = arguments.length == 0 ? null : CoreProtectNames.permission(arguments[0]);
            return command.testPermissionSilent(sender) && permission != null && sender.hasPermission(permission);
        }
        @Override public boolean onCommand(CommandSender sender, Command ignored, String label, String[] arguments) {
            if (!allowed(sender, arguments)) return previous.onCommand(sender, command, label, arguments);
            CoreProtectNames.Result translated;
            try {
                translated = names.rewrite(arguments);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Component.text("[BerryNames] " + e.getMessage(), NamedTextColor.RED));
                return true;
            }
            if (showMapping && !translated.mappings().isEmpty()) {
                String mapping = translated.mappings().stream().map(entry -> entry.displayName() + " → " + entry.accountName()).collect(Collectors.joining(", "));
                sender.sendMessage(Component.text("[BerryNames] " + mapping, NamedTextColor.GRAY));
            }
            return previous.onCommand(sender, command, label, translated.arguments());
        }
        @Override public List<String> onTabComplete(CommandSender sender, Command ignored, String label, String[] arguments) {
            List<String> original = completer != null ? completer.onTabComplete(sender, command, label, arguments)
                    : previous instanceof TabCompleter fallback ? fallback.onTabComplete(sender, command, label, arguments) : null;
            List<String> additions = allowed(sender, arguments) ? names.suggestions(arguments) : List.of();
            if (additions.isEmpty()) return original;
            var result = new LinkedHashSet<String>();
            if (original != null) result.addAll(original);
            result.addAll(additions); return List.copyOf(result);
        }
        void restore() {
            if (command.getExecutor() == this) command.setExecutor(previous);
            if (command.getTabCompleter() == this) command.setTabCompleter(completer);
        }
    }
}
