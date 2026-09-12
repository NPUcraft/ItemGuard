package com.npucraft.itemguard;

import org.bukkit.plugin.java.JavaPlugin;

public final class ItemGuardPlugin extends JavaPlugin {

    private ItemGuardServices services;

    @Override
    public void onEnable() {
        printBanner();
        services = new ItemGuardServices(this);
        services.enable();
    }

    @Override
    public void onDisable() {
        if (services != null) {
            services.disable();
            services = null;
        }
    }

    private void printBanner() {
        for (String line : StartupBanner.lines()) {
            getLogger().info(line);
        }
    }
}
