package com.npucraft.itemguard.api;

public final class ItemGuardApiProvider {

    private static volatile ItemGuardApi instance;

    private ItemGuardApiProvider() {
    }

    public static ItemGuardApi get() {
        ItemGuardApi api = instance;
        if (api == null) {
            throw new IllegalStateException("ItemGuard is not enabled");
        }
        return api;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    public static void register(ItemGuardApi api) {
        instance = api;
    }

    public static void unregister() {
        instance = null;
    }
}
