package dev.itemguard.harness;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public final class ItemGuardTestHarnessPlugin extends JavaPlugin {

    static final Gson GSON = new GsonBuilder().serializeNulls().create();
    static final String DEFAULT_PLAYER = "ItemGuardBot";

    private FixtureService fixture;
    private ScenarioService scenarios;
    private DiagnosticExporter diagnostics;
    private HarnessHttpServer http;
    private HuskSyncEventLog eventLog;
    private Object huskSyncAdmin;

    @Override
    public void onEnable() {
        this.fixture = new FixtureService(this);
        this.scenarios = new ScenarioService(this, fixture);
        this.diagnostics = new DiagnosticExporter(this);
        this.eventLog = new HuskSyncEventLog();
        Bukkit.getPluginManager().registerEvents(fixture, this);
        if (Bukkit.getPluginManager().getPlugin("HuskSync") != null) {
            try {
                Class<?> observer = Class.forName("dev.itemguard.harness.HuskSyncEventObserver");
                Object listener = observer.getConstructor(ItemGuardTestHarnessPlugin.class).newInstance(this);
                Bukkit.getPluginManager().registerEvents((org.bukkit.event.Listener) listener, this);
                getLogger().info("HuskSync event observer registered");
                try {
                    Class<?> admin = Class.forName("dev.itemguard.harness.HuskSyncAdmin");
                    this.huskSyncAdmin = admin.getConstructor(ItemGuardTestHarnessPlugin.class).newInstance(this);
                    getLogger().info("HuskSync admin helper registered");
                } catch (Exception exception) {
                    getLogger().log(Level.WARNING, "Could not register HuskSync admin helper", exception);
                }
            } catch (Exception exception) {
                getLogger().log(Level.WARNING, "Could not register HuskSync event observer", exception);
            }
        }
        var command = getCommand("igh");
        if (command != null) {
            HarnessCommand handler = new HarnessCommand(this, scenarios, diagnostics);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        Bukkit.getScheduler().runTask(this, fixture::ensureReady);
        try {
            this.http = new HarnessHttpServer(this, scenarios, diagnostics, fixture);
            this.http.start();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to start harness HTTP server", exception);
            throw new IllegalStateException("ItemGuard Test Harness HTTP server failed", exception);
        }
        getLogger().info("ItemGuard Test Harness enabled (integration only)");
    }

    @Override
    public void onDisable() {
        if (http != null) {
            http.stop();
        }
    }

    FixtureService fixture() {
        return fixture;
    }

    DiagnosticExporter diagnostics() {
        return diagnostics;
    }

    HuskSyncEventLog eventLog() {
        return eventLog;
    }

    String serverId() {
        String property = System.getProperty("itemguard.server.id", System.getenv("ITEMGUARD_SERVER_ID"));
        if (property == null || property.isBlank()) {
            return "integration";
        }
        return property;
    }

    Object huskSyncAdmin() {
        return huskSyncAdmin;
    }

    Path reportsDir() {
        String configured = System.getProperty("itemguard.reports.dir", System.getenv("ITEMGUARD_REPORTS_DIR"));
        if (configured == null || configured.isBlank()) {
            return Path.of("integration-tests", "reports");
        }
        return Path.of(configured);
    }

    static String playerName(JsonObject body) {
        if (body != null && body.has("player") && !body.get("player").isJsonNull()) {
            String name = body.get("player").getAsString();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        String env = System.getenv("ITEMGUARD_BOT_NAME");
        return env == null || env.isBlank() ? DEFAULT_PLAYER : env;
    }

    static Player requirePlayer(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            throw new IllegalStateException("Player not online: " + name);
        }
        return player;
    }

    static Map<String, Object> ok(Map<String, Object> extra) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", true);
        if (extra != null) {
            map.putAll(extra);
        }
        return map;
    }

    static GameMode parseMode(String mode) {
        if (mode == null) {
            return GameMode.SURVIVAL;
        }
        return GameMode.valueOf(mode.trim().toUpperCase());
    }
}
