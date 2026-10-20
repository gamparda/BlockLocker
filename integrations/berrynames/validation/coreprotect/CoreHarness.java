package verification;

import kr.berrynames.api.*;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.coreprotect.consumer.Queue;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

public final class CoreHarness extends JavaPlugin {
    private final UUID first=UUID.fromString("00000000-0000-4000-8000-000000000501"), second=UUID.fromString("00000000-0000-4000-8000-000000000502");
    private CoreProtectAPI api;
    private JavaPlugin bridge;
    private Block one,two;
    private int checks;
    private Sender admin=new Sender("CoreFixture",true), denied=new Sender("CoreDenied",false);
    @Override public void onEnable() { later(60,()->{
        try {
            check(Bukkit.getVersion().contains("201"),"Paper 26.3 build 201");
            bridge=(JavaPlugin)Bukkit.getPluginManager().getPlugin("BerryIntegrations");
            var core=(CoreProtect)Bukkit.getPluginManager().getPlugin("CoreProtect"); api=core.getAPI();
            check(bridge.isEnabled()&&core.isEnabled(),"CoreProtect and integration enabled");
            check(api.isEnabled()&&api.APIVersion()>=11,"CoreProtect public API version "+api.APIVersion());
            check(Bukkit.getServicesManager().getRegistrations(AliasAwareCommands.class).stream().anyMatch(reg->reg.getProvider().handles(core.getCommand("co"))),"BerryNames skips native argument rewriting for CoreProtect");
            one=Bukkit.getWorlds().getFirst().getBlockAt(20,85,20); two=one.getRelative(1,0,0);
            if(Files.exists(Path.of("core-success.ok"))) { restart(); return; }
            one.setType(Material.STONE); two.setType(Material.DIRT);
            QueueFixture.login(player("CpFirst",first),0); QueueFixture.login(player("CpSecond",second),0);
            check(api.logPlacement("CpFirst",one.getState()),"public API accepts first placement");
            check(api.logPlacement("CpSecond",two.getState()),"public API accepts second placement");
            query(()->{
                var firstRows=api.performLookup(86400,List.of("CpFirst"),null,null,null,null,0,null);
                var secondRows=api.performLookup(86400,List.of("CpSecond"),null,null,null,null,0,null);
                return firstRows!=null&&!firstRows.isEmpty()&&secondRows!=null&&!secondRows.isEmpty();
            },()->{
                check(true,"actual CoreProtect SQLite logs persisted for both accounts");
                query(()->api.usernameLookup("CpFirst",86400).stream().anyMatch(row->row.getUuid().equals(first.toString())),()->{
                    check(true,"native stored UUID survives alias integration"); lookup();
                });
            });
        } catch(Throwable e){fail(e);}
    }); }
    private void lookup() {
        admin.messages.clear(); command(admin,"co lookup u:광부대장 t:1h r:#global");
        await(()->admin.nativeText().toLowerCase(Locale.ROOT).contains("cpfirst")&&admin.nativeText().toLowerCase(Locale.ROOT).contains("stone"),()->{
            check(admin.text().contains("광부대장 → CpFirst"),"visible alias-to-account mapping");
            check(!admin.nativeText().toLowerCase(Locale.ROOT).contains("cpsecond"),"native lookup restricts exact translated account");
            var completion=Bukkit.getPluginCommand("co").tabComplete(admin.sender,"co",new String[]{"lookup","u:베"});
            check(completion.contains("u:베리사")&&completion.contains("u:베리오"),"native command completion includes offline aliases");
            command(denied,"co rollback u:베리사 t:1h r:#global");
            check(!denied.text().contains("→"),"denied command does not expose mappings");
            check(one.getType()==Material.STONE,"permission denial preserves blocks");
            later(25,()->{
                admin.messages.clear(); command(admin,"coreprotect:co lookup u:베리사,베리오 e:베리오 t:1h r:#global");
                await(()->admin.nativeText().toLowerCase(Locale.ROOT).contains("cpfirst")&&admin.nativeText().toLowerCase(Locale.ROOT).contains("stone"),()->{
                    check(!admin.nativeText().toLowerCase(Locale.ROOT).contains("cpsecond"),"native mixed include/exclude aliases respect excluded account"); rename();
                });
            });
        });
    }
    private void rename() {
        command(admin,"bn set #4 새광부");
        check(Bukkit.getServicesManager().load(BerryNamesApi.class).find(first).orElseThrow().displayName().equals("새광부"),"real BerryNames administrator rename");
        later(25,()->{
            admin.messages.clear(); command(admin,"core lookup users:새광부 t:1h r:#global");
            await(()->admin.nativeText().toLowerCase(Locale.ROOT).contains("cpfirst")&&admin.nativeText().toLowerCase(Locale.ROOT).contains("stone"),()->{
                check(admin.text().contains("새광부 → CpFirst"),"rename immediately resolves existing logs"); rollback();
            });
        });
    }
    private void rollback() {
        command(admin,"co rollback u:베리사 t:1h r:#global");
        await(()->one.getType()==Material.AIR,()->{
            check(two.getType()==Material.DIRT,"native rollback changes selected user only");
            later(25,()->{
                command(admin,"coreprotect restore u:"+first+" t:1h r:#global");
                await(()->one.getType()==Material.STONE,()->{
                    check(two.getType()==Material.DIRT,"native restore accepts UUID and preserves other user");
                    lifecycle();
                });
            });
        });
    }
    private void lifecycle() {
        var core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("CoreProtect");
        var wrapped=core.getCommand("co").getExecutor();
        Bukkit.getPluginManager().disablePlugin(bridge);
        check(core.getCommand("co").getExecutor()!=wrapped,"addon disable restores native executor");
        check(Bukkit.getServicesManager().getRegistrations(AliasAwareCommands.class).stream().noneMatch(reg->reg.getPlugin()==bridge),"addon disable removes alias-aware service");
        bridge.getConfig().set("bluemap.enabled",false);
        Bukkit.getPluginManager().enablePlugin(bridge);
        check(core.getCommand("co").getExecutor().getClass().getName().contains("CoreProtectHook"),"addon re-enable reinstalls wrapper");
        command(admin,"co reload");
        later(80,()->{
            check(core.getCommand("co").getExecutor().getClass().getName().contains("CoreProtectHook"),"CoreProtect reload retains native command wrapper");
            admin.messages.clear(); command(admin,"co lookup p:#4 t:1h r:#global");
            await(()->admin.nativeText().toLowerCase(Locale.ROOT).contains("cpfirst")&&admin.nativeText().toLowerCase(Locale.ROOT).contains("stone"),()->{
                check(true,"native lookup after CoreProtect reload");
                query(()->api.usernameLookup("CpFirst",86400).stream().anyMatch(row->row.getUuid().equals(first.toString())),()->{
                    check(true,"UUID history remains unchanged after lookup rollback restore and reload");
                    command(admin,"bn set #4 광부대장"); success("core-success.ok");
                });
            });
        });
    }
    private void restart() {
        check(one.getType()==Material.STONE&&two.getType()==Material.DIRT,"restored world blocks persist across restart");
        admin.messages.clear(); command(admin,"co lookup u:베리사 t:1h r:#global");
        await(()->admin.nativeText().toLowerCase(Locale.ROOT).contains("cpfirst")&&admin.nativeText().toLowerCase(Locale.ROOT).contains("stone"),()->{
            check(true,"native alias lookup reads previous database after full restart");
            query(()->api.usernameLookup("CpFirst",86400).stream().anyMatch(row->row.getUuid().equals(first.toString())),()->{
                check(true,"stored original UUID persists across full restart"); success("core-restart.ok");
            });
        });
    }
    private void query(BooleanSupplier condition,Runnable next) {
        int[] attempts={0};
        var task=new org.bukkit.scheduler.BukkitRunnable(){public void run(){
            try {if(condition.getAsBoolean()){cancel();later(1,next);} else if(++attempts[0]>40){cancel();later(1,()->fail(new AssertionError("Database query timed out")));}}
            catch(Throwable e){cancel();later(1,()->fail(e));}
        }};task.runTaskTimerAsynchronously(this,20,20);
    }
    private void await(BooleanSupplier condition,Runnable next) {
        int[] attempts={0};
        new org.bukkit.scheduler.BukkitRunnable(){public void run(){try{
            if(condition.getAsBoolean()){cancel();next.run();}
            else if(++attempts[0]>50){cancel();fail(new AssertionError("Native operation timed out; messages="+admin.text()));}
        }catch(Throwable e){cancel();fail(e);}}}.runTaskTimer(this,10,10);
    }
    private void later(long ticks,Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable e){fail(e);}},ticks);}
    private void command(Sender sender,String text){
        // Paper normalizes a ConsoleCommandSender through its Brigadier console source.
        // Execute the real PluginCommand directly to retain this capturing test sender.
        String[] parts=text.split(" ");
        var command=Bukkit.getPluginCommand(parts[0]);
        check(command!=null&&command.execute(sender.sender,parts[0],Arrays.copyOfRange(parts,1,parts.length)),"native command /"+text);
    }
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;getLogger().info("PASS "+label);}
    private void success(String file){try{Files.writeString(Path.of(file),checks+" checks passed on "+Bukkit.getVersion()+" / CoreProtect API "+api.APIVersion());}catch(Exception e){fail(e);return;}getLogger().info("CORE_SUCCESS "+checks);Bukkit.shutdown();}
    private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"CORE_FAILURE",e);try{Files.writeString(Path.of("core-failure.txt"),e.toString());}catch(Exception ignored){}Bukkit.shutdown();}
    private Player player(String name,UUID uuid){return (Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()){
        case "getName"->name;case "getUniqueId"->uuid;case "getWorld"->Bukkit.getWorlds().getFirst();case "getLocation"->one.getLocation();
        case "hashCode"->uuid.hashCode();case "equals"->p==a[0];default->zero(m.getReturnType());
    });}
    public static class QueueFixture extends Queue {static void login(Player player,int mode){queuePlayerLogin(player,(int)(System.currentTimeMillis()/1000),mode,1);}}
    private final class Sender implements InvocationHandler {
        final String name; final boolean allowed; final ConsoleCommandSender sender;
        final List<String> messages=new CopyOnWriteArrayList<>();
        final CommandSender.Spigot spigot=new CommandSender.Spigot(){
            @Override public void sendMessage(BaseComponent component){messages.add(component.toPlainText());}
            @Override public void sendMessage(BaseComponent... components){messages.add(BaseComponent.toPlainText(components));}
        };
        Sender(String name,boolean allowed){this.name=name;this.allowed=allowed;sender=(ConsoleCommandSender)Proxy.newProxyInstance(getClassLoader(),new Class[]{ConsoleCommandSender.class},this);}
        String text(){return String.join("\n",messages);}String nativeText(){return messages.stream().filter(s->!s.contains("[BerryNames]")).reduce("",(a,b)->a+"\n"+b);}
        @Override public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
            case "getName","toString"->name;case "getServer"->Bukkit.getServer();case "isOp"->allowed;
            case "hasPermission","isPermissionSet"->allowed||String.valueOf(a[0]).equals("coreprotect.co");
            case "spigot"->spigot;case "sendMessage"->{if(a!=null)for(Object value:a){if(value instanceof net.kyori.adventure.text.Component component)messages.add(PlainTextComponentSerializer.plainText().serialize(component));else if(value instanceof String[] texts)messages.addAll(List.of(texts));else messages.add(String.valueOf(value));}yield null;}
            case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];default->zero(m.getReturnType());
        };}
    }
    private static Object zero(Class<?> type){if(!type.isPrimitive()||type==void.class)return null;if(type==boolean.class)return false;if(type==long.class)return 0L;if(type==float.class)return 0f;if(type==double.class)return 0d;if(type==byte.class)return (byte)0;if(type==short.class)return (short)0;if(type==char.class)return '\0';return 0;}
}
