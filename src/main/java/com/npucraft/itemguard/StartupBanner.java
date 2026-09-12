package com.npucraft.itemguard;

final class StartupBanner {

    static final String CREDIT = "ItemGuard by NPUcraft";

    private StartupBanner() {
    }

    static String[] lines() {
        return new String[] {
            "",
            "  ___ _                 ____                     _",
            " |_ _| |_ ___ _ __ ___ / ___|_   _  __ _ _ __ __| |",
            "  | || __/ _ \\ '_ ` _ \\| |  | | | |/ _` | '__/ _` |",
            "  | || ||  __/ | | | | | |__| |_| | (_| | | | (_| |",
            " |___|\\__\\___|_| |_| |_|\\____\\__,_|\\__,_|_|  \\__,_|",
            "              " + CREDIT,
            ""
        };
    }
}
