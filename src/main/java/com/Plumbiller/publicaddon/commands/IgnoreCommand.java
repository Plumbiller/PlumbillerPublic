package com.Plumbiller.publicaddon.commands;

import com.Plumbiller.publicaddon.util.FileManager;
import com.Plumbiller.publicaddon.util.MultiVersionCompat;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.stream.Collectors;

public class IgnoreCommand extends Command {
    public IgnoreCommand() {
        super("ignore", "Adds a player to the ignore list.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("add")
                .then(argument("player", StringArgumentType.greedyString())
                        .suggests((context, suggestionsBuilder) -> {
                            if (mc.getNetworkHandler() != null) {
                                return CommandSource.suggestMatching(mc.getNetworkHandler().getPlayerList()
                                        .stream().map(p -> MultiVersionCompat.getProfileName(p.getProfile())),
                                        suggestionsBuilder);
                            }
                            return suggestionsBuilder.buildFuture();
                        })
                        .executes(context -> {
                            String player = context.getArgument("player", String.class);
                            return addIgnoredPlayer(player);
                        })));

        builder.then(literal("remove")
                .then(argument("player", StringArgumentType.greedyString())
                        .suggests((context, suggestionsBuilder) -> {
                            try {
                                List<String> lines = Files.readAllLines(FileManager.getIgnoredPlayersFile());
                                return CommandSource.suggestMatching(lines, suggestionsBuilder);
                            } catch (IOException e) {
                                return suggestionsBuilder.buildFuture();
                            }
                        })
                        .executes(context -> {
                            String player = context.getArgument("player", String.class);
                            return removeIgnoredPlayer(player);
                        })));

        builder.then(literal("list")
                .executes(context -> listIgnoredPlayers()));
    }

    private int addIgnoredPlayer(String player) {
        if (player.equals("NotS*") || player.equals("NotSu*") || player.equals("NotSus*") || player.equals("Plumbiller")
                || player.equals("Plumbille*") || player.equals("Plumbill*") || player.equals("Plumbil*")
                || player.equals("Plumbi*") || player.equals("Plumb*") || player.equals("Plum*")) {
            // Funny error message
            error(getRandomFunnySentence());
            return SINGLE_SUCCESS;
        }
        try {
            List<String> lines = Files.readAllLines(FileManager.getIgnoredPlayersFile());
            if (lines.contains(player)) {
                info("Player %s is already ignored.", player);
                return SINGLE_SUCCESS;
            }
            Files.writeString(FileManager.getIgnoredPlayersFile(), player + "\n", StandardOpenOption.APPEND);
            info("Added %s to ignored list.", player);
        } catch (IOException e) {
            error("Failed to write to ignored players file.");
            e.printStackTrace();
        }
        return SINGLE_SUCCESS;
    }

    private int removeIgnoredPlayer(String player) {
        try {
            List<String> lines = Files.readAllLines(FileManager.getIgnoredPlayersFile());
            if (lines.remove(player)) {
                Files.write(FileManager.getIgnoredPlayersFile(), lines);
                info("Removed %s from ignored list.", player);
            } else {
                error("Player %s is not in the ignored list.", player);
            }
        } catch (IOException e) {
            error("Failed to update ignored players file.");
            e.printStackTrace();
        }
        return SINGLE_SUCCESS;
    }

    private int listIgnoredPlayers() {
        try {
            List<String> lines = Files.readAllLines(FileManager.getIgnoredPlayersFile());
            if (lines.isEmpty()) {
                info("No players are currently ignored.");
            } else {
                String list = String.join(", ", lines);
                info("Ignored players: %s", list);
            }
        } catch (IOException e) {
            error("Failed to read ignored players file.");
            e.printStackTrace();
        }
        return SINGLE_SUCCESS;
    }

    private String getRandomFunnySentence() {
        try (InputStream stream = IgnoreCommand.class.getClassLoader().getResourceAsStream("funny_sentences.txt")) {
            if (stream == null)
                return "That's rude.";
            List<String> lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.toList());
            if (lines.isEmpty())
                return "That's rude.";
            return lines.get(new Random().nextInt(lines.size()));
        } catch (IOException e) {
            e.printStackTrace();
            return "That's rude.";
        }
    }
}
