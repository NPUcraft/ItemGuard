package com.npucraft.itemguard.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Map;

public final class Texts {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Texts() {
    }

    public static Component parse(String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) {
            return Component.empty();
        }
        return MINI.deserialize(miniMessage);
    }

    public static Component parse(String miniMessage, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return parse(miniMessage);
        }
        TagResolver[] resolvers = placeholders.entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue() == null ? "" : entry.getValue()))
                .toArray(TagResolver[]::new);
        return MINI.deserialize(miniMessage, TagResolver.resolver(resolvers));
    }
}
