package com.npucraft.itemguard.scan;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface ItemScanRule {

    String id();

    List<ScanFinding> scan(ItemStack stack, ScanContext context);
}
