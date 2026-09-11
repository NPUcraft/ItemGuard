package dev.itemguard.testplugin;

import dev.itemguard.api.ItemGuardApiProvider;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Acceptance-only helper. Not part of the ItemGuard product JAR.
 */
public final class ItemGuardTestPlugin extends JavaPlugin implements Listener {

    private boolean cancelDrops;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ItemGuardTestPlugin enabled (acceptance helper only)");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /igtest <api|illegal|custom|oversized|legal-maxstack|signature|cancel-drop|shulker|bundle> [player]");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if ("cancel-drop".equals(action)) {
            cancelDrops = !cancelDrops;
            sender.sendMessage("cancel-drop=" + cancelDrops);
            return true;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player player ? player : null);
        if (target == null) {
            sender.sendMessage("Player not online.");
            return true;
        }
        return switch (action) {
            case "api" -> apiGrant(sender, target);
            case "illegal" -> give(sender, target, illegalSword());
            case "custom" -> give(sender, target, customSword());
            case "oversized" -> give(sender, target, oversizedStick());
            case "legal-maxstack" -> give(sender, target, legalWideStack());
            case "signature" -> signature(sender, target);
            case "shulker" -> give(sender, target, filledShulker());
            case "bundle" -> give(sender, target, filledBundle());
            default -> {
                sender.sendMessage("Unknown action.");
                yield true;
            }
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (cancelDrops) {
            event.setCancelled(true);
        }
    }

    private boolean apiGrant(CommandSender sender, Player target) {
        if (!ItemGuardApiProvider.isAvailable()) {
            sender.sendMessage("ItemGuard API is not available.");
            return true;
        }
        ItemGuardApiProvider.get().recordExpectedGain(
                target.getUniqueId(),
                "DIAMOND",
                64,
                "itemguard-test",
                "acceptance-" + UUID.randomUUID()
        );
        target.getInventory().addItem(new ItemStack(Material.DIAMOND, 64));
        sender.sendMessage("API expected gain + addItem 64 DIAMOND for " + target.getName());
        return true;
    }

    private boolean give(CommandSender sender, Player target, ItemStack stack) {
        target.getInventory().addItem(stack);
        sender.sendMessage("Gave " + stack.getType() + " x" + stack.getAmount() + " to " + target.getName());
        return true;
    }

    private ItemStack illegalSword() {
        ItemStack stack = new ItemStack(Material.DIAMOND_SWORD);
        stack.addUnsafeEnchantment(Enchantment.SHARPNESS, 255);
        return stack;
    }

    private ItemStack customSword() {
        ItemStack stack = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("RPG Sword"));
        meta.lore(List.of(Component.text("A perfectly legal custom item")));
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "rpg"), PersistentDataType.STRING, "ok");
        AttributeModifier modifier = new AttributeModifier(
                new NamespacedKey(this, "rpg-damage"),
                1.0,
                AttributeModifier.Operation.ADD_NUMBER
        );
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, modifier);
        stack.setItemMeta(meta);
        try {
            meta = stack.getItemMeta();
            meta.setCustomModelData(12);
            stack.setItemMeta(meta);
        } catch (RuntimeException ignored) {
            // Paper may reject legacy CustomModelData setters on some items; scan still sees name/lore/PDC.
        }
        return stack;
    }

    private ItemStack oversizedStick() {
        ItemStack stack = new ItemStack(Material.DIAMOND_SWORD);
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.setAmount(2);
        return stack;
    }

    private ItemStack legalWideStack() {
        ItemStack stack = new ItemStack(Material.DIAMOND_SWORD);
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        stack.setAmount(8);
        return stack;
    }

    private ItemStack filledShulker() {
        ItemStack box = new ItemStack(Material.SHULKER_BOX);
        if (box.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof org.bukkit.block.ShulkerBox shulker) {
            for (int slot = 0; slot < 27; slot++) {
                shulker.getInventory().setItem(slot, new ItemStack(Material.DIAMOND, 64));
            }
            meta.setBlockState(shulker);
            box.setItemMeta(meta);
        }
        return box;
    }

    private ItemStack filledBundle() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        if (bundle.getItemMeta() instanceof BundleMeta meta) {
            meta.addItem(new ItemStack(Material.DIAMOND, 64));
            bundle.setItemMeta(meta);
        }
        return bundle;
    }

    private boolean signature(CommandSender sender, Player target) {
        ItemStack held = target.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            sender.sendMessage("Hold an item.");
            return true;
        }
        String first = hash(held);
        int same = 0;
        for (int i = 0; i < 100; i++) {
            if (first.equals(hash(held))) {
                same++;
            }
        }
        ItemStack amount64 = held.clone();
        amount64.setAmount(Math.min(64, Math.max(1, amount64.getMaxStackSize())));
        String hash64 = hash(amount64);
        ItemStack renamed = held.clone();
        ItemMeta meta = renamed.getItemMeta();
        meta.displayName(Component.text("sig-change-" + UUID.randomUUID()));
        renamed.setItemMeta(meta);
        sender.sendMessage("100 identical=" + (same == 100) + " (" + same + "/100)");
        sender.sendMessage("hash=" + first);
        sender.sendMessage("amount-normalized same=" + first.equals(hash64));
        sender.sendMessage("name change different=" + !first.equals(hash(renamed)));
        if (held.getItemMeta() instanceof Damageable) {
            ItemStack damaged = held.clone();
            if (damaged.getItemMeta() instanceof Damageable damage) {
                damage.setDamage(Math.max(1, damage.getDamage() + 1));
                damaged.setItemMeta(damage);
                sender.sendMessage("damage change different=" + !first.equals(hash(damaged)));
            }
        }
        return true;
    }

    private static String hash(ItemStack stack) {
        ItemStack clone = stack.clone();
        clone.setAmount(1);
        try {
            byte[] bytes = clone.serializeAsBytes();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            return "error:" + exception.getMessage();
        }
    }
}
