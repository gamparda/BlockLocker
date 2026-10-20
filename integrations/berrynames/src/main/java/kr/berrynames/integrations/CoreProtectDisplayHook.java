package kr.berrynames.integrations;

import kr.berrynames.api.BerryNamesApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Version-checked CoreProtect rendering adapter. No database/cache mutation or global chat listener. */
final class CoreProtectDisplayHook implements AutoCloseable {
    private final Field adapter;
    private final Class<?> contract;
    private final CoreProtectDisplay display;
    private final Consumer<Throwable> failure;
    private final BukkitTask refresh;
    private Object previous, installed;
    private volatile boolean closed;
    private volatile boolean warned;

    CoreProtectDisplayHook(JavaPlugin owner, JavaPlugin coreProtect, Supplier<BerryNamesApi> names,
                           boolean displayNames, boolean translateMaterials, Consumer<Throwable> failure) throws ReflectiveOperationException {
        if (!coreProtect.getPluginMeta().getVersion().equals("24.1"))
            throw new IllegalArgumentException("CoreProtect output adapter requires the verified 24.1 version");
        this.failure=failure;
        ClassLoader loader=coreProtect.getClass().getClassLoader();
        Class<?> type=Class.forName("net.coreprotect.spigot.SpigotAdapter",false,loader);
        adapter=type.getField("ADAPTER"); contract=adapter.getType();
        if (!Modifier.isStatic(adapter.getModifiers())||Modifier.isFinal(adapter.getModifiers())||!contract.isInterface())
            throw new IllegalArgumentException("Unsupported CoreProtect rendering adapter");
        contract.getMethod("sendComponent",CommandSender.class,String.class,String.class);
        String legacy=(String)Class.forName("net.coreprotect.utility.Color",true,loader).getField("DARK_AQUA").get(null);
        display=new CoreProtectDisplay(names,translateMaterials ? translationKeys() : Map.of(),
                LegacyComponentSerializer.legacySection().deserialize(legacy+"x").color(),displayNames);
        attach();
        refresh=owner.getServer().getScheduler().runTaskTimer(owner,()->{
            try { attach(); } catch (ReflectiveOperationException | RuntimeException e) { warn(e); }
        },20,20);
    }

    private static Map<String,String> translationKeys() {
        var result=new HashMap<String,String>();
        for(Material material:Material.values()) {
            if(material.isLegacy()) continue;
            try {
                result.put(material.name().toLowerCase(Locale.ROOT),material.translationKey());
                result.put(material.getKey().toString(),material.translationKey());
            } catch(IllegalArgumentException ignored) { }
        }
        for(EntityType entity:EntityType.values()) {
            if(entity==EntityType.UNKNOWN) continue;
            try {
                result.putIfAbsent(entity.name().toLowerCase(Locale.ROOT),entity.translationKey());
                result.putIfAbsent(entity.getKey().toString(),entity.translationKey());
            } catch(IllegalArgumentException ignored) { }
        }
        return Map.copyOf(result);
    }

    private synchronized void attach() throws IllegalAccessException {
        if(closed) return;
        Object current=adapter.get(null);
        if(current==installed||current==null) return;
        // Reconnect after CoreProtect reload resets its native renderer, without fighting other addons.
        if(installed!=null&&!Set.of("net.coreprotect.spigot.SpigotHandler","net.coreprotect.spigot.SpigotAdapter").contains(current.getClass().getName())) return;
        Object delegate=current;
        Object proxy=Proxy.newProxyInstance(contract.getClassLoader(),new Class[]{contract},(self,method,args)->{
            if(method.getDeclaringClass()==Object.class) return switch(method.getName()){
                case "equals"->self==args[0];case "hashCode"->System.identityHashCode(self);default->"BerryNames CoreProtect display adapter";
            };
            Object[] actual=args;
            if(!closed&&method.getName().equals("sendComponent")&&CoreProtectDisplay.isRow((String)args[1])) {
                actual=args.clone();
                actual[0]=capture((CommandSender)args[0],CoreProtectDisplay.hasMaterial((String)args[1],(String)args[2]));
            }
            return invoke(method,delegate,actual);
        });
        adapter.set(null,proxy);previous=delegate;installed=proxy;
    }

    private CommandSender capture(CommandSender sender,boolean materials) {
        CommandSender.Spigot spigot=new CommandSender.Spigot(){
            private void send(BaseComponent... parts){
                Component message;
                try { message=display.decorate(GsonComponentSerializer.gson().deserialize(ComponentSerializer.toString(parts)),materials); }
                catch(RuntimeException|LinkageError error){warn(error);sender.spigot().sendMessage(parts);return;}
                sender.sendMessage(message);
            }
            @Override public void sendMessage(BaseComponent part){send(part);}
            @Override public void sendMessage(BaseComponent... parts){send(parts);}
            @Override public void sendMessage(UUID uuid,BaseComponent part){send(part);}
            @Override public void sendMessage(UUID uuid,BaseComponent... parts){send(parts);}
        };
        Class<?> type=sender instanceof ConsoleCommandSender ? ConsoleCommandSender.class : CommandSender.class;
        return (CommandSender)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(self,method,args)->
                method.getName().equals("spigot") ? spigot : invoke(method,sender,args));
    }
    private static Object invoke(Method method,Object target,Object[] args) throws Throwable {
        try {return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}
    }
    private synchronized void warn(Throwable error){if(!warned){warned=true;failure.accept(error);}}
    @Override public synchronized void close() throws IllegalAccessException {
        closed=true;refresh.cancel();
        if(adapter.get(null)==installed)adapter.set(null,previous);
    }
}
