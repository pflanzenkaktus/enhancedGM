package de.germanminer.addon.map;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.germanminer.addon.config.AddonConfig;
import de.germanminer.addon.data.SpeedCameraRepository;
import de.germanminer.addon.feature.BlitzerWarningTracker;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Lightweight localhost HTTP bridge (default http://127.0.0.1:2087/) for second-monitor use.
 * Renders a live-centered BlueMap view with Speed Camera overlays and live player telemetry.
 */
public class LocalWebMapBridge {
    private static final Gson GSON = new Gson();

    private final AddonConfig config;
    private final SpeedCameraRepository cameraRepository;
    private HttpServer server;

    private volatile double playerX = 0.0;
    private volatile double playerY = 64.0;
    private volatile double playerZ = 0.0;
    private volatile float playerYaw = 0.0f;
    private volatile double speedKmh = 0.0;
    private volatile BlitzerWarningTracker.BlitzerAlert activeAlert = null;

    public LocalWebMapBridge(AddonConfig config, SpeedCameraRepository cameraRepository) {
        this.config = config;
        this.cameraRepository = cameraRepository;
    }

    public synchronized void startIfEnabled() {
        if (!config.enableLocalhostWebBridge || server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", config.localhostBridgePort), 0);
            server.createContext("/", this::handleIndexPage);
            server.createContext("/api/state", this::handleStateApi);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "GM-Addon-LocalWebMapBridge");
                t.setDaemon(true);
                return t;
            }));
            server.start();
        } catch (IOException e) {
            System.err.println("[GM-Addon] Could not start localhost web map bridge on port "
                + config.localhostBridgePort + ": " + e.getMessage());
            server = null;
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void updatePlayerTelemetry(
        double x,
        double y,
        double z,
        float yaw,
        double speedKmh,
        BlitzerWarningTracker.BlitzerAlert alert
    ) {
        this.playerX = x;
        this.playerY = y;
        this.playerZ = z;
        this.playerYaw = yaw;
        this.speedKmh = speedKmh;
        this.activeAlert = alert;
    }

    public String getLocalUrl() {
        return "http://127.0.0.1:" + config.localhostBridgePort + "/";
    }

    private void handleStateApi(HttpExchange exchange) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("x", playerX);
        payload.put("y", playerY);
        payload.put("z", playerZ);
        payload.put("yaw", playerYaw);
        payload.put("speedKmh", speedKmh);
        payload.put("cameras", cameraRepository.getCameras());
        payload.put("alert", activeAlert);
        payload.put("blueMapBaseUrl", config.blueMapBaseUrl);
        payload.put("blueMapWorldId", config.blueMapWorldId);

        byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleIndexPage(HttpExchange exchange) throws IOException {
        String html = """
            <!DOCTYPE html>
            <html lang="de">
            <head>
              <meta charset="utf-8">
              <title>GermanMiner Live Player Map (GM-Addon)</title>
              <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { background: #0f172a; color: #f8fafc; font-family: system-ui, -apple-system, sans-serif; overflow: hidden; height: 100vh; display: flex; flex-direction: column; }
                header { background: #1e293b; padding: 10px 18px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #334155; }
                .badge { background: #dc2626; color: #fff; font-weight: 700; padding: 4px 10px; border-radius: 6px; display: none; }
                .stats { display: flex; gap: 18px; font-size: 14px; color: #cbd5e1; align-items: center; }
                #map-wrap { position: relative; flex: 1; width: 100%; height: 100%; }
                iframe { width: 100%; height: 100%; border: none; }
                .controls { display: flex; gap: 10px; align-items: center; }
                button, label { background: #334155; color: #f8fafc; border: 1px solid #475569; padding: 5px 10px; border-radius: 6px; cursor: pointer; font-size: 13px; }
                button:hover { background: #475569; }
              </style>
            </head>
            <body>
              <header>
                <div style="display:flex;align-items:center;gap:14px;">
                  <strong>GM-Addon Live Map</strong>
                  <span id="alert-badge" class="badge">BLITZER WARNUNG</span>
                </div>
                <div class="stats">
                  <span id="pos-readout">X: 0 | Z: 0</span>
                  <span id="speed-readout">0 km/h</span>
                </div>
                <div class="controls">
                  <label><input type="checkbox" id="follow-toggle" checked> Auto-Follow Player</label>
                  <button onclick="recenterNow()">Jetzt zentrieren</button>
                </div>
              </header>
              <div id="map-wrap">
                <iframe id="bluemap-frame" src="http://map.germanminer.de:2086/#world:0:0:0:500:0:0:0:0:flat"></iframe>
              </div>
              <script>
                let lastState = null;
                let zoomDist = 350;
                const frame = document.getElementById('bluemap-frame');
                const followToggle = document.getElementById('follow-toggle');

                function recenterNow() {
                  if (!lastState) return;
                  const x = Math.round(lastState.x);
                  const z = Math.round(lastState.z);
                  const base = lastState.blueMapBaseUrl || 'http://map.germanminer.de:2086';
                  const world = lastState.blueMapWorldId || 'world';
                  frame.src = `${base}/#${world}:${x}:0:${z}:${zoomDist}:0:0:0:0:flat`;
                }

                async function poll() {
                  try {
                    const res = await fetch('/api/state');
                    const data = await res.json();
                    const moved = !lastState || Math.hypot(data.x - lastState.x, data.z - lastState.z) >= 2.0;
                    lastState = data;
                    document.getElementById('pos-readout').textContent =
                      `X: ${Math.round(data.x)} | Y: ${Math.round(data.y)} | Z: ${Math.round(data.z)}`;
                    document.getElementById('speed-readout').textContent =
                      `${Math.round(data.speedKmh || 0)} km/h`;

                    const badge = document.getElementById('alert-badge');
                    if (data.alert && data.alert.camera) {
                      badge.style.display = 'inline-block';
                      badge.textContent = `BLITZER (${data.alert.speedLimitKmh} km/h): ${data.alert.camera.name} in ${Math.round(data.alert.distanceBlocks)}m`;
                    } else {
                      badge.style.display = 'none';
                    }

                    if (followToggle.checked && moved) {
                      recenterNow();
                    }
                  } catch (e) {}
                }
                setInterval(poll, 450);
                poll();
              </script>
            </body>
            </html>
            """;
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
