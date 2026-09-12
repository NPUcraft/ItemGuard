package com.npucraft.itemguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupBannerTest {

    @Test
    void bannerCreditsNpucraft() {
        String joined = String.join("\n", StartupBanner.lines());
        assertTrue(joined.contains("ItemGuard by NPUcraft"));
        assertTrue(joined.contains("ItemGuard"));
    }
}
