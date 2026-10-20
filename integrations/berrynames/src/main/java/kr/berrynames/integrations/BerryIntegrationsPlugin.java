package kr.berrynames.integrations;

import kr.berrynames.api.BerryNamesApi;
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
    }

    @Override public void onDisable() {
        for (int i = hooks.size() - 1; i >= 0; i--) {
            try { hooks.get(i).close(); }
            catch (Exception e) { getLogger().log(Level.WARNING, "이름 제공자 복구 실패", e); }
        }
        hooks.clear();
    }
}
