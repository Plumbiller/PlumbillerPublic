package com.Plumbiller.publicaddon.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager.RestrictedArea;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AllowedPlayerArgumentType implements ArgumentType<String> {

    public static AllowedPlayerArgumentType create() {
        return new AllowedPlayerArgumentType();
    }

    public static String get(CommandContext<?> context) {
        return context.getArgument("player", String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        try {
            String areaName = context.getArgument("area", String.class);
            String serverIp = getServerIp();

            if (serverIp != null && areaName != null) {
                Optional<RestrictedArea> area = RestrictedAreaManager.getRestrictedArea(serverIp, areaName);
                if (area.isPresent()) {
                    for (String player : area.get().getAllowedPlayers()) {
                        builder.suggest(player);
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
        }

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("Steve", "Alex");
    }

    private String getServerIp() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isIntegratedServerRunning()) {
            return "singleplayer";
        }

        ServerInfo serverInfo = mc.getCurrentServerEntry();
        if (serverInfo != null) {
            return serverInfo.address;
        }

        return null;
    }
}

