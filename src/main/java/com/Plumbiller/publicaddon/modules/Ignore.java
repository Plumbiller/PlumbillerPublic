package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.Main;
import com.Plumbiller.publicaddon.util.FileManager;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Ignore extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> messageFormat = sgGeneral.add(new StringSetting.Builder()
            .name("message-format")
            .description("Format of the chat messages. Use {player} for the sender and {message} for the content.")
            .defaultValue("[Rank] {player} » {message}")
            .build());

    private final Setting<String> whisperFormat = sgGeneral.add(new StringSetting.Builder()
            .name("whisper-format")
            .description("Format of the whisper messages. Use {player} for the sender and {message} for the content.")
            .defaultValue("{player} whispers: {message}")
            .build());

    public Ignore() {
        super(Main.CATEGORY, "ignore", "Ignores messages from specified players.");
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String message = event.getMessage().getString();

        if (processMessage(message, messageFormat.get())) {
            event.cancel();
            return;
        }

        if (processMessage(message, whisperFormat.get())) {
            event.cancel();
        }
    }

    private boolean processMessage(String message, String format) {
        String regex = Pattern.quote(format);

        regex = regex.replace("[Rank]", "\\E(?:.*\\])?\\Q");

        regex = regex.replace("{player}", "\\E(?<player>.*?)\\Q");
        regex = regex.replace("{message}", "\\E(?<message>.*)\\Q");

        regex = regex.replace(" ", "\\E\\s*\\Q");

        regex = regex.replace("\\Q\\E", "");

        regex = "^" + regex + "$";

        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(message);

            if (matcher.find()) {
                String player = matcher.group("player");

                if (player != null) {
                    player = player.trim();
                    return isIgnored(player);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean isIgnored(String player) {
        if (player == null)
            return false;
        try {
            List<String> ignored = Files.readAllLines(FileManager.getIgnoredPlayersFile());
            for (String line : ignored) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                String patternStr = "^" + Pattern.quote(line).replace("*", "\\E.*\\Q") + "$";
                patternStr = patternStr.replace("\\Q\\E", "");

                if (player.matches(patternStr)) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
