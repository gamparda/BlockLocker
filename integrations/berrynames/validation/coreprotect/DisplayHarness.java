package verification;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.coreprotect.consumer.Queue;
import net.coreprotect.listener.player.inspector.BlockInspector;
import net.coreprotect.spigot.SpigotAdapter;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
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

public final class DisplayHarness extends JavaPlugin {
    private final UUID uuid=UUID.fromString("00000000-0000-4000-8000-000000000504");
    private final List<Component> messages=new CopyOnWriteArrayList<>();
    private CoreProtectAPI api; private Block grass; private Player viewer; private int checks;
    @Override public void onEnable(){later(60,()->{
        check(Bukkit.getVersion().contains("201"),"Paper 26.3 build 201");
        check(Bukkit.getPluginManager().isPluginEnabled("BerryIntegrations"),"BerryIntegrations 1.2.0 enabled");
        api=((CoreProtect)Bukkit.getPluginManager().getPlugin("CoreProtect")).getAPI();
        check(Proxy.isProxyClass(SpigotAdapter.ADAPTER.getClass()),"native CoreProtect rendering adapter connected");
        grass=Bukkit.getWorlds().getFirst().getBlockAt(24,85,24); viewer=player("InspectFixture",UUID.randomUUID());
        if(Files.exists(Path.of("display-success.ok"))){inspect("감파르다",()->{check(true,"native inspector display survives full restart");success("display-restart.ok");});return;}
        grass.setType(Material.GRASS_BLOCK);
        QueueFixture.login(player("gamparda",uuid));
        check(api.logPlacement("gamparda",grass.getState()),"log real grass-block placement with actual account");
        query(()->{var rows=api.performLookup(86400,List.of("gamparda"),null,null,null,null,0,null);return rows!=null&&!rows.isEmpty();},()->{
            inspect("감파르다",()->{
                writeJson("display-inspect.json");
                check(all().stream().anyMatch(c->c.hoverEvent()!=null&&String.valueOf(c.hoverEvent().value()).contains(uuid.toString())),"actor hover contains original UUID");
                check(all().stream().anyMatch(c->c.hoverEvent()!=null&&String.valueOf(c.hoverEvent().value()).contains("gamparda")),"actor hover contains real account");
                check(messages.stream().anyMatch(c->plain(c).contains("설치")),"existing Korean CoreProtect phrase preserved");
                command("co lookup u:감파르다 t:1h r:#global");
                await(()->hasActor("감파르다")&&hasMaterial(),()->{
                    writeJson("display-lookup.json");check(true,"native command lookup uses same actor and block display");
                    command("bn set #7 새감파");
                    later(25,()->inspect("새감파",()->{
                        check(true,"native inspector reflects alias changes without changing logs");
                        query(()->api.usernameLookup("gamparda",86400).stream().anyMatch(r->r.getUuid().equals(uuid.toString())),()->{
                            check(true,"stored original username UUID unchanged");
                            command("co reload");later(60,()->inspect("새감파",()->{
                                check(true,"native inspector survives CoreProtect reload");
                                Object old=SpigotAdapter.ADAPTER;SpigotAdapter.loadAdapter();check(SpigotAdapter.ADAPTER!=old,"native renderer reset fixture");
                                await(()->Proxy.isProxyClass(SpigotAdapter.ADAPTER.getClass()),()->inspect("새감파",()->{
                                    check(true,"renderer reset reconnects automatically");
                                    command("bn set #7 감파르다");
                                    query(()->{var rows=api.performLookup(86400,List.of("감파르다"),null,null,null,null,0,null);return rows==null||rows.isEmpty();},()->{
                                        check(true,"alias was never written into log identity");
                                        check(grass.getType()==Material.GRASS_BLOCK,"display formatting does not alter world blocks");
                                        success("display-success.ok");
                                    });
                                }));
                            }));
                        });
                    }));
                });
            });
        });
    });}
    private void inspect(String name,Runnable next){
        messages.clear();new BlockInspector().performBlockLookup(viewer,grass.getState());
        await(()->hasActor(name)&&hasMaterial(),()->{check(true,"actual native BlockInspector shows "+name+" with grass-block translation");next.run();});
    }
    private boolean hasActor(String name){return all().stream().anyMatch(c->c instanceof TextComponent t&&t.content().equals(name));}
    private boolean hasMaterial(){return all().stream().anyMatch(c->c instanceof TranslatableComponent t&&t.key().equals("block.minecraft.grass_block"));}
    private List<Component> all(){var result=new ArrayList<Component>();messages.forEach(c->flatten(c,result));return result;}
    private void flatten(Component c,List<Component> result){result.add(c);c.children().forEach(child->flatten(child,result));}
    private String plain(Component c){return PlainTextComponentSerializer.plainText().serialize(c);}
    private void writeJson(String name){try{Files.writeString(Path.of(name),GsonComponentSerializer.gson().serialize(Component.empty().append(messages)));}catch(Exception e){throw new RuntimeException(e);}}
    private void command(String text){messages.clear();String[] args=text.split(" ");var cmd=Bukkit.getPluginCommand(args[0]);check(cmd!=null&&cmd.execute(viewer,args[0],Arrays.copyOfRange(args,1,args.length)),"native /"+text);}
    private void query(BooleanSupplier condition,Runnable next){int[] tries={0};new org.bukkit.scheduler.BukkitRunnable(){public void run(){try{if(condition.getAsBoolean()){cancel();later(1,next);}else if(++tries[0]>40){cancel();later(1,()->fail(new AssertionError("Database fixture timeout")));}}catch(Throwable e){cancel();later(1,()->fail(e));}}}.runTaskTimerAsynchronously(this,20,20);}
    private void await(BooleanSupplier condition,Runnable next){int[] tries={0};new org.bukkit.scheduler.BukkitRunnable(){public void run(){try{if(condition.getAsBoolean()){cancel();next.run();}else if(++tries[0]>60){cancel();fail(new AssertionError("Output timeout: "+messages.stream().map(DisplayHarness.this::plain).toList()));}}catch(Throwable e){cancel();fail(e);}}}.runTaskTimer(this,10,10);}
    private void later(long ticks,Runnable work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable e){fail(e);}},ticks);}
    private void check(boolean okay,String label){if(!okay)throw new AssertionError(label);checks++;getLogger().info("PASS "+label);}
    private void success(String name){try{Files.writeString(Path.of(name),checks+" checks passed on "+Bukkit.getVersion());}catch(Exception e){throw new RuntimeException(e);}getLogger().info("DISPLAY_SUCCESS "+checks);Bukkit.shutdown();}
    private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"DISPLAY_FAILURE",e);try{Files.writeString(Path.of("display-failure.txt"),e.toString());}catch(Exception ignored){}Bukkit.shutdown();}
    public static class QueueFixture extends Queue{static void login(Player p){queuePlayerLogin(p,(int)(System.currentTimeMillis()/1000),0,1);}}
    private Player player(String name,UUID id){
        Player.Spigot spigot=new Player.Spigot(){
            @Override public void sendMessage(BaseComponent part){messages.add(GsonComponentSerializer.gson().deserialize(ComponentSerializer.toString(part)));}
            @Override public void sendMessage(BaseComponent... parts){messages.add(GsonComponentSerializer.gson().deserialize(ComponentSerializer.toString(parts)));}
        };
        return (Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getName","toString"->name;case "getUniqueId"->id;case "getWorld"->grass.getWorld();case "getLocation"->grass.getLocation();
            case "hasPermission","isPermissionSet","isOp","isOnline","isValid","canSee"->true;
            case "getServer"->Bukkit.getServer();case "getGameMode"->GameMode.CREATIVE;case "spigot"->spigot;
            case "sendMessage"->{if(a!=null)for(Object v:a){if(v instanceof Component c)messages.add(c);else if(v instanceof String s)messages.add(Component.text(s));else if(v instanceof String[] ss)for(String s:ss)messages.add(Component.text(s));}yield null;}
            case "hashCode"->id.hashCode();case "equals"->p==a[0];default->zero(m.getReturnType());
        });
    }
    private static Object zero(Class<?> t){if(!t.isPrimitive()||t==void.class)return null;if(t==boolean.class)return false;if(t==long.class)return 0L;if(t==float.class)return 0f;if(t==double.class)return 0d;if(t==byte.class)return(byte)0;if(t==short.class)return(short)0;if(t==char.class)return '\0';return 0;}
}
