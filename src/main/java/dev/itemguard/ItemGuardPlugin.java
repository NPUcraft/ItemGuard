package dev.itemguard;

import org.bukkit.plugin.java.JavaPlugin;

public final class ItemGuardPlugin extends JavaPlugin {

    private ItemGuardServices services;

    @Override
    public void onEnable() {
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
}
