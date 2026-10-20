package kr.berrynames.integrations;

import kr.berrynames.api.BerryNamesApi;
import kr.berrynames.api.AliasAwareCommands;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class BerryIntegrationsPlugin extends JavaPlugin {
    private final List<AutoCloseable> hooks = new ArrayList<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        if (getServer().getServicesManager().load(BerryNamesApi.class) == null) {
            getLogger().severe("BerryNames 1.3.0 이상의 이름 서비스가 필요합니다. BerryNames JAR를 업데이트하세요.");
            getServer().getPluginManager().disablePlugin(this); return;
        }
        if (getConfig().getBoolean("bluemap.enabled", true) && getServer().getPluginManager().isPluginEnabled("BlueMap")) {
            try {
                hooks.add(new BlueMapHook(() -> getServer().getServicesManager().load(BerryNamesApi.class),
                        e -> getLogger().log(Level.WARNING, "BlueMap 이름표 스타일 적용 실패. 웹 루트 쓰기 권한을 확인하세요.", e)));
                getLogger().info("BlueMap 이름 표시 연동 등록 완료.");
            } catch (LinkageError | RuntimeException e) { getLogger().log(Level.SEVERE, "BlueMap 이름 API 연동 실패. BlueMap 5.29에서 검증한 API가 필요합니다.", e); }
        }
        if (getConfig().getBoolean("blocklocker.enabled", true) && getServer().getPluginManager().isPluginEnabled("BlockLocker")) {
            try {
                hooks.add(new BlockLockerHook(getServer().getPluginManager().getPlugin("BlockLocker"),
                        () -> getServer().getServicesManager().load(BerryNamesApi.class)));
                getLogger().info("BlockLocker UUID 이름 연동 활성화.");
            } catch (LinkageError | RuntimeException e) { getLogger().log(Level.SEVERE, "BlockLocker 이름 연동 실패. 함께 제공한 gamparda 포크 빌드가 필요합니다.", e); }
        }
        if (getConfig().getBoolean("coreprotect.enabled", true) && getServer().getPluginManager().isPluginEnabled("CoreProtect")) {
            try {
                var hook = new CoreProtectHook((JavaPlugin) getServer().getPluginManager().getPlugin("CoreProtect"),
                        () -> getServer().getServicesManager().load(BerryNamesApi.class), getConfig().getBoolean("coreprotect.show-mapping", true));
                hooks.add(hook);
                getServer().getServicesManager().register(AliasAwareCommands.class, hook, this, ServicePriority.Normal);
                getLogger().info("CoreProtect 베리 이름 명령어 연동 활성화.");
                boolean displayNames=getConfig().getBoolean("coreprotect.display-names",true);
                boolean translateMaterials=getConfig().getBoolean("coreprotect.translate-materials",true);
                if(displayNames||translateMaterials) {
                    try {
                        hooks.add(new CoreProtectDisplayHook(this,(JavaPlugin)getServer().getPluginManager().getPlugin("CoreProtect"),
                                ()->getServer().getServicesManager().load(BerryNamesApi.class),displayNames,translateMaterials,
                                e->getLogger().log(Level.WARNING,"CoreProtect 결과 표시 변환 실패. 해당 결과는 원문으로 표시합니다.",e)));
                        getLogger().info("CoreProtect 검사·조회 결과의 베리 이름 및 블록명 표시 연동 활성화.");
                    } catch(ReflectiveOperationException|RuntimeException|LinkageError e) {
                        getLogger().log(Level.WARNING,"CoreProtect 결과 표시 연결 실패. 명령어 이름 연동은 유지됩니다. 검증 버전은 24.1입니다.",e);
                    }
                }
            } catch (LinkageError | RuntimeException e) { getLogger().log(Level.SEVERE, "CoreProtect 명령어 연동 실패. CoreProtect 24.1에서 검증했습니다.", e); }
        }
    }

    @Override public void onDisable() {
        for (int i = hooks.size() - 1; i >= 0; i--) {
            try { hooks.get(i).close(); }
            catch (Exception e) { getLogger().log(Level.WARNING, "이름 제공자 복구 실패", e); }
        }
        hooks.clear();
        getServer().getServicesManager().unregisterAll(this);
    }
}
