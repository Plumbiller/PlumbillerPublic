package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.Main;
import com.Plumbiller.publicaddon.util.FileManager;
import com.Plumbiller.publicaddon.util.MultiVersionCompat;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WhisperLogger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> receiveFormat = sgGeneral.add(new StringSetting.Builder()
            .name("receive-format")
            .description("Format for received whispers. Use {player} for the sender and {message} for the content.")
            .defaultValue("{player} whispers: {message}")
            .build());

    private final Setting<String> sendFormat = sgGeneral.add(new StringSetting.Builder()
            .name("send-format")
            .description("Format for sent whispers. Use {player} for the receiver and {message} for the content.")
            .defaultValue("You whisper to {player}: {message}")
            .build());

    public enum TimeFormat {
        DDMMYYYY_HHMM("DD/MM/YYYY HH:MM", "dd/MM/yyyy HH:mm"),
        MMDDYYYY_HHMM("MM/DD/YYYY HH:MM", "MM/dd/yyyy HH:mm"),
        YYYYMMDD_HHMM("YYYY-MM-DD HH:MM", "yyyy-MM-dd HH:mm");

        private final String title;
        private final String pattern;

        TimeFormat(String title, String pattern) {
            this.title = title;
            this.pattern = pattern;
        }

        public String getPattern() {
            return pattern;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final Setting<TimeFormat> timeFormat = sgGeneral.add(new EnumSetting.Builder<TimeFormat>()
            .name("time-format")
            .description("Time format for the log timestamp.")
            .defaultValue(TimeFormat.DDMMYYYY_HHMM)
            .build());

    public WhisperLogger() {
        super(Main.CATEGORY, "whisper-logger",
                "Logs whispers to a formatted html archive that tries to mimic discord aesthetic. The logs are saved in your PlumbillerAddon folder in your minecraft installation folder.");
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String message = event.getMessage().getString();
        if (checkAndLog(message, receiveFormat.get(), true)) {
            return;
        }
        checkAndLog(message, sendFormat.get(), false);
    }

    private boolean checkAndLog(String content, String format, boolean isReceive) {
        try {
            String regex = "^" + Pattern.quote(format)
                    .replace("{player}", "\\E(?<player>.*?)\\Q")
                    .replace("{message}", "\\E(?<message>.*?)\\Q") + "$";

            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(content);

            if (matcher.find()) {
                String otherPlayer = matcher.group("player");
                String messageContent = matcher.group("message");

                String myName = "Unknown";
                if (mc.player != null) {
                    myName = MultiVersionCompat.getProfileName(mc.player.getGameProfile());
                }

                String sender = isReceive ? otherPlayer : myName;

                logHtml(otherPlayer, sender, messageContent);
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    private void logHtml(String otherPlayer, String sender, String message) {
        Path addonFolder = FileManager.getAddonFolder();
        Path logsDir = addonFolder.resolve("WhisperLogs");
        Path logFile = logsDir.resolve(otherPlayer + ".html");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(timeFormat.get().getPattern());
        String timestamp = LocalDateTime.now().format(formatter);

        try {
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }

            boolean isNewFile = !Files.exists(logFile);
            StringBuilder content = new StringBuilder();

            if (isNewFile) {
                content.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<style>\n");
                content.append(
                        "body { background-color: #36393f; color: #dcddde; font-family: sans-serif; padding: 10px; }\n");
                content.append(".msg { display: flex; margin-bottom: 15px; }\n");
                content.append(".avatar { width: 40px; height: 40px; border-radius: 50%; margin-right: 15px; }\n");
                content.append(".content { display: flex; flex-direction: column; }\n");
                content.append(".header { display: flex; align-items: baseline; }\n");
                content.append(".username { font-weight: bold; margin-right: 5px; color: #fff; }\n");
                content.append(".timestamp { font-size: 0.75rem; color: #72767d; }\n");
                content.append(".text { margin-top: 2px; line-height: 1.4; color: #dcddde; }\n");
                content.append("</style>\n</head>\n<body>\n");
                content.append(
                        "<h2 style=\"color: #fff; text-align: center; margin-bottom: 20px;\">PlumbillerPublic Meteor Addon. Whisper Logger.</h2>\n");
            }

            String avatarUrl = "https://mc-heads.net/avatar/" + sender + "/32";
            content.append("<div class=\"msg\">\n");
            content.append(String.format("<img class=\"avatar\" src=\"%s\">\n", avatarUrl));
            content.append("<div class=\"content\">\n");
            content.append("<div class=\"header\">\n");
            content.append(String.format("<span class=\"username\">%s</span>\n", sender));
            content.append(String.format("<span class=\"timestamp\">%s</span>\n", timestamp));
            content.append("</div>\n");
            content.append(String.format("<div class=\"text\">%s</div>\n", escapeHtml(message)));
            content.append("</div>\n</div>\n");

            Files.writeString(logFile, content.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
