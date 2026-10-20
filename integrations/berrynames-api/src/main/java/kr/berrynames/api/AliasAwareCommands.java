package kr.berrynames.api;

import org.bukkit.command.Command;

/** Register when the owning plugin's commands resolve BerryNames aliases themselves. */
public interface AliasAwareCommands {
    boolean handles(Command command);
}
