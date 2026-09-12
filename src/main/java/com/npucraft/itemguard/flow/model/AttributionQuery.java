package com.npucraft.itemguard.flow.model;

import java.util.UUID;

/**
 * Ranking hint for expected-flow consumption. Exact credits still win over hints.
 */
public record AttributionQuery(
        ContainerIdentity preferredContainer,
        UUID preferredInteraction,
        FlowSource preferredSource
) {
    public static AttributionQuery none() {
        return new AttributionQuery(null, null, null);
    }
}
