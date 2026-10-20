package kr.berrynames.integrations;

import kr.berrynames.api.BerryNamesApi;
import org.bukkit.command.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

class CoreProtectHookTest {
    final PluginCommand command = mock(PluginCommand.class);
    final CommandExecutor executor = mock(CommandExecutor.class);
    final TabCompleter completer = mock(TabCompleter.class);
    final CommandSender sender = mock(CommandSender.class);
    CoreProtectNames names() {
        var identity = new BerryNamesApi.Identity(UUID.randomUUID(),1,"RealAccount","베리일","베리일");
        return new CoreProtectNames(() -> new BerryNamesApi() {
            public Optional<Identity> find(UUID uuid) { return Optional.of(identity).filter(i -> i.uuid().equals(uuid)); }
            public List<Identity> identities() { return List.of(identity); }
            public List<Identity> findAliases(String value) { return value.equals("베리일") ? List.of(identity) : List.of(); }
        });
    }
    CoreProtectHook.Binding bind(boolean permission) {
        when(command.getExecutor()).thenReturn(executor); when(command.getTabCompleter()).thenReturn(completer);
        when(command.testPermissionSilent(sender)).thenReturn(true); when(sender.hasPermission(anyString())).thenReturn(permission);
        return new CoreProtectHook.Binding(command,names(),false);
    }
    @Test void delegatesToNativeExecutorWithSameSenderAndLabel() {
        var binding = bind(true);
        binding.onCommand(sender,command,"coreprotect:co",new String[]{"rollback","u:베리일","t:1h","#preview"});
        verify(executor).onCommand(same(sender),same(command),eq("coreprotect:co"),aryEq(new String[]{"rollback","u:RealAccount","t:1h","#preview"}));
    }
    @Test void permissionDenialDoesNotRewriteOrExposeMappings() {
        var binding = bind(false); String[] args={"lookup","u:베리일"};
        binding.onCommand(sender,command,"co",args);
        verify(executor).onCommand(sender,command,"co",args); verify(sender).hasPermission("coreprotect.lookup"); verifyNoMoreInteractions(sender);
        when(completer.onTabComplete(any(),any(),anyString(),any())).thenReturn(List.of("native"));
        assertEquals(List.of("native"),binding.onTabComplete(sender,command,"co",args));
    }
    @Test void tabCompletionKeepsNativeSuggestions() {
        var binding = bind(true);
        when(completer.onTabComplete(any(),any(),anyString(),any())).thenReturn(List.of("native"));
        assertEquals(List.of("native","u:베리일"),binding.onTabComplete(sender,command,"co",new String[]{"lookup","u:베"}));
    }
    @Test void restoreDoesNotOverwriteAnotherPluginsLaterWrapper() {
        var binding = bind(true);
        when(command.getExecutor()).thenReturn(binding); when(command.getTabCompleter()).thenReturn(binding);
        binding.restore(); verify(command).setExecutor(executor); verify(command).setTabCompleter(completer);
        clearInvocations(command);
        when(command.getExecutor()).thenReturn(mock(CommandExecutor.class)); when(command.getTabCompleter()).thenReturn(mock(TabCompleter.class));
        binding.restore(); verify(command,never()).setExecutor(any()); verify(command,never()).setTabCompleter(any());
    }
}
