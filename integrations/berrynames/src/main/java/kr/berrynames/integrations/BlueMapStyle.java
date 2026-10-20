package kr.berrynames.integrations;

import de.bluecolored.bluemap.api.WebApp;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

final class BlueMapStyle {
    // Change the URL when changing the stylesheet to bypass existing browser caches.
    static final String URL = "assets/berrynames/player-names-v1.css";

    private BlueMapStyle() { }

    static void install(WebApp webApp) throws IOException {
        var destination = webApp.getWebRoot().resolve(URL);
        Files.createDirectories(destination.getParent());
        try (var css = BlueMapStyle.class.getResourceAsStream("/bluemap/player-names.css")) {
            if (css == null) throw new IOException("Missing bundled BlueMap player-name stylesheet");
            Files.copy(css, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        webApp.registerStyle(URL);
    }
}
