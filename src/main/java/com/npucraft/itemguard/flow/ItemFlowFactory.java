package com.npucraft.itemguard.flow;

import com.npucraft.itemguard.flow.model.ContainerIdentity;
import com.npucraft.itemguard.flow.model.FlowDestination;
import com.npucraft.itemguard.flow.model.FlowSource;
import com.npucraft.itemguard.flow.model.ItemFlowEvent;
import com.npucraft.itemguard.flow.model.LocationRef;
import com.npucraft.itemguard.flow.model.SourceConfidence;
import com.npucraft.itemguard.item.ItemSignature;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.UUID;

public final class ItemFlowFactory {

    private ItemFlowFactory() {
    }

    public static ItemFlowEvent create(
            Player player,
            Instant timestamp,
            String material,
            ItemSignature signature,
            int amountDelta,
            FlowSource source,
            FlowDestination destination,
            SourceConfidence confidence,
            LocationRef location,
            ContainerIdentity container,
            String note,
            UUID correlationId
    ) {
        LocationRef loc = location == null ? LocationRef.unknown() : location;
        return new ItemFlowEvent(
                UUID.randomUUID(),
                player.getUniqueId(),
                player.getName(),
                timestamp,
                material,
                signature == null ? ItemSignature.materialOnly(material) : signature,
                amountDelta,
                source,
                destination,
                confidence,
                loc.worldName(),
                loc.worldId(),
                loc.x(),
                loc.y(),
                loc.z(),
                container,
                note,
                correlationId
        );
    }
}
