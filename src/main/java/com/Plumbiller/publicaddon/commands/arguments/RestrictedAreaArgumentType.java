package com.Plumbiller.publicaddon.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager.RestrictedArea;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager.ServerData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RestrictedAreaArgumentType implements ArgumentType<String> {

    public static RestrictedAreaArgumentType create() {
        return new RestrictedAreaArgumentType();
    }

    public static String get(CommandContext<?> context) {
        return context.getArgument("area", String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String serverIp = getServerIp();
        if (serverIp != null) {
            Optional<ServerData> serverData = RestrictedAreaManager.getServerData(serverIp);
            if (serverData.isPresent()) {
                for (RestrictedArea area : serverData.get().getRestrictedAreas()) {
                    builder.suggest(area.getName());
                }
            }
        }
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("spawn", "base", "farm");
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

