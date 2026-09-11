package dev.itemguard.harness;

import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HarnessCommand implements CommandExecutor, TabCompleter {

    private final ItemGuardTestHarnessPlugin plugin;
    private final ScenarioService scenarios;
    private final DiagnosticExporter diagnostics;

    HarnessCommand(ItemGuardTestHarnessPlugin plugin, ScenarioService scenarios, DiagnosticExporter diagnostics) {
        this.plugin = plugin;
        this.scenarios = scenarios;
        this.diagnostics = diagnostics;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /igh <reset|prepare|kill|creative|survival|diagnostic|identify|events> [player] [scenario]");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        String playerName = args.length >= 2 ? args[1] : ItemGuardTestHarnessPlugin.DEFAULT_PLAYER;
        try {
            Map<String, Object> result = switch (action) {
                case "identify" -> diagnostics.identify(playerName);
                case "events" -> plugin.eventLog().dump();
                default -> {
                    Player player = ItemGuardTestHarnessPlugin.requirePlayer(playerName);
                    yield switch (action) {
                        case "reset" -> scenarios.reset(player);
                        case "prepare" -> scenarios.prepare(player, args.length >= 3 ? args[2] : null);
                        case "kill" -> scenarios.kill(player);
                        case "creative" -> scenarios.setGameMode(player, GameMode.CREATIVE);
                        case "survival" -> scenarios.setGameMode(player, GameMode.SURVIVAL);
                        case "result", "diagnostic" -> diagnostics.dump(player);
                        default -> Map.of("ok", false, "error", "Unknown action");
                    };
                }
            };
            sender.sendMessage("ITEMGUARD_TEST_JSON:" + ItemGuardTestHarnessPlugin.GSON.toJson(result));
        } catch (RuntimeException exception) {
            sender.sendMessage("ITEMGUARD_TEST_JSON:" + ItemGuardTestHarnessPlugin.GSON.toJson(Map.of(
                    "ok", false,
                    "error", exception.getMessage()
            )));
            plugin.getLogger().warning("/igh failed: " + exception.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("reset", "prepare", "kill", "creative", "survival", "diagnostic", "identify", "events");
        }
        if (args.length == 3 && "prepare".equalsIgnoreCase(args[0])) {
            return List.of(
                    "pickup-one",
                    "pickup-stack",
                    "chest-normal",
                    "chest-shift",
                    "chest-partial",
                    "craft",
                    "bundle",
                    "bundle-chest",
                    "filled-shulker",
                    "placed-shulker",
                    "illegal-enchant",
                    "custom-item",
                    "death-diamonds"
            );
        }
        return List.of();
    }
}
