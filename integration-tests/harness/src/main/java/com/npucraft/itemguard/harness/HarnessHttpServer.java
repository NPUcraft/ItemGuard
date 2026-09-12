package com.npucraft.itemguard.harness;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class HarnessHttpServer {

    private final ItemGuardTestHarnessPlugin plugin;
    private final ScenarioService scenarios;
    private final DiagnosticExporter diagnostics;
    private final FixtureService fixture;
    private HttpServer server;
    private int port;

    HarnessHttpServer(
            ItemGuardTestHarnessPlugin plugin,
            ScenarioService scenarios,
            DiagnosticExporter diagnostics,
            FixtureService fixture
    ) {
        this.plugin = plugin;
        this.scenarios = scenarios;
        this.diagnostics = diagnostics;
        this.fixture = fixture;
    }

    void start() throws IOException {
        this.port = resolvePort();
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.start();
        writePortFile();
        plugin.getLogger().info("ITEMGUARD_HARNESS_READY port=" + port);
    }

    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            CompletableFuture<Response> future = new CompletableFuture<>();
            Runnable work = () -> {
                try {
                    future.complete(dispatch(method, path, query, body));
                } catch (Exception exception) {
                    future.complete(Response.error(500, formatError(exception)));
                }
            };
            if (offMainThread(path, body)) {
                work.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, work);
            }
            Response response = future.get(20, TimeUnit.SECONDS);
            send(exchange, response);
        } catch (Exception exception) {
            try {
                send(exchange, Response.error(500, formatError(exception)));
            } catch (IOException ignored) {
                // The client already disconnected.
            }
        }
    }

    private Response dispatch(String method, String path, String query, String body) {
        JsonObject json = parse(body);
        String playerName = ItemGuardTestHarnessPlugin.playerName(json);
        return switch (path) {
            case "/health" -> Response.ok(Map.of("ok", true, "ready", fixture.ready(), "port", port));
            case "/environment" -> Response.ok(diagnostics.environment());
            case "/reset" -> Response.ok(scenarios.reset(ItemGuardTestHarnessPlugin.requirePlayer(playerName)));
            case "/prepare" -> Response.ok(scenarios.prepare(ItemGuardTestHarnessPlugin.requirePlayer(playerName), text(json, "scenario")));
            case "/drop" -> Response.ok(scenarios.dropDiamonds(
                    ItemGuardTestHarnessPlugin.requirePlayer(playerName),
                    json.has("amount") ? json.get("amount").getAsInt() : 1,
                    json.has("reset") && json.get("reset").getAsBoolean()
            ));
            case "/kill" -> Response.ok(scenarios.kill(ItemGuardTestHarnessPlugin.requirePlayer(playerName)));
            case "/scan" -> Response.ok(scenarios.scan(ItemGuardTestHarnessPlugin.requirePlayer(playerName)));
            case "/gamemode" -> Response.ok(scenarios.setGameMode(
                    ItemGuardTestHarnessPlugin.requirePlayer(playerName),
                    ItemGuardTestHarnessPlugin.parseMode(text(json, "mode"))
            ));
            case "/gamerule" -> Response.ok(scenarios.setKeepInventory(
                    ItemGuardTestHarnessPlugin.requirePlayer(playerName),
                    json.has("keepInventory") && json.get("keepInventory").getAsBoolean()
            ));
            case "/diagnostic", "/result" -> Response.ok(diagnostics.dump(ItemGuardTestHarnessPlugin.requirePlayer(playerName)));
            case "/identify" -> Response.ok(diagnostics.identify(playerName));
            case "/events" -> Response.ok(plugin.eventLog().dump());
            case "/husksync" -> Response.ok(huskSync(playerName, json));
            case "/inventory" -> {
                Player player = ItemGuardTestHarnessPlugin.requirePlayer(playerName);
                yield Response.ok(Map.of(
                        "ok", true,
                        "player", player.getName(),
                        "inventory", diagnostics.inventoryCounts(player)
                ));
            }
            default -> Response.error(404, "Unknown path: " + method + " " + path + (query == null ? "" : "?" + query));
        };
    }

    private Map<String, Object> huskSync(String playerName, JsonObject json) {
        Object helper = plugin.huskSyncAdmin();
        if (helper == null) {
            throw new IllegalStateException("HuskSync is not available on this backend");
        }
        HuskSyncAdmin admin = (HuskSyncAdmin) helper;
        String action = text(json, "action");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action is required");
        }
        return switch (action.toLowerCase()) {
            case "save" -> admin.save(ItemGuardTestHarnessPlugin.requirePlayer(playerName));
            case "list" -> admin.list(ItemGuardTestHarnessPlugin.requirePlayer(playerName));
            case "restore" -> admin.restore(ItemGuardTestHarnessPlugin.requirePlayer(playerName), text(json, "snapshotId"));
            default -> throw new IllegalArgumentException("Unknown husksync action: " + action);
        };
    }

    private static boolean offMainThread(String path, String body) {
        if (!"/husksync".equals(path)) {
            return false;
        }
        String action = text(parse(body), "action");
        return action != null && "list".equalsIgnoreCase(action);
    }

    private static String formatError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }

    private static JsonObject parse(String body) {
        if (body == null || body.isBlank()) {
            return new JsonObject();
        }
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private static String text(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        return json.get(key).getAsString();
    }

    private static void send(HttpExchange exchange, Response response) throws IOException {
        byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(response.status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private int resolvePort() {
        String property = System.getProperty("itemguard.harness.port", System.getenv("ITEMGUARD_HARNESS_PORT"));
        if (property != null && !property.isBlank()) {
            return Integer.parseInt(property);
        }
        return 0;
    }

    private void writePortFile() {
        try {
            Path runtime = Path.of(System.getProperty("itemguard.runtime.dir", "build/integration-runtime"));
            Files.createDirectories(runtime);
            Files.writeString(runtime.resolve("harness.port"), Integer.toString(port), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not write harness.port: " + exception.getMessage());
        }
    }

    private record Response(int status, String body) {
        static Response ok(Map<String, Object> map) {
            return new Response(200, ItemGuardTestHarnessPlugin.GSON.toJson(map));
        }

        static Response error(int status, String message) {
            return new Response(status, ItemGuardTestHarnessPlugin.GSON.toJson(Map.of(
                    "ok", false,
                    "error", message == null ? "error" : message
            )));
        }
    }
}
