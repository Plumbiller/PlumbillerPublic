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
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class NotAllowedPlayerArgumentType implements ArgumentType<PlayerListEntry> {

    public static NotAllowedPlayerArgumentType create() {
        return new NotAllowedPlayerArgumentType();
    }

    public static PlayerListEntry get(CommandContext<?> context) {
        return context.getArgument("player", PlayerListEntry.class);
    }

    @Override
    public PlayerListEntry parse(StringReader reader) throws CommandSyntaxException {
        String playerName = reader.readString();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                if (entry.getProfile().getName().equals(playerName)) {
                    return entry;
                }
            }
        }

        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.getNetworkHandler() != null) {
            try {
                String areaName = context.getArgument("area", String.class);
                String serverIp = getServerIp();

                if (serverIp != null && areaName != null) {
                    Optional<RestrictedArea> area = RestrictedAreaManager.getRestrictedArea(serverIp, areaName);

                    for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                        String playerName = entry.getProfile().getName();

                        if (area.isEmpty() || !area.get().getAllowedPlayers().contains(playerName)) {
                            builder.suggest(playerName);
                        }
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
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
