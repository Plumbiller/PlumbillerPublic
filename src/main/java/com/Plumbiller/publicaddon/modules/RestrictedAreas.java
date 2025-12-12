package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.Main;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;

enum RenderStyle {
    Solid,
    Pulsing
}

enum RequestState {
    ENABLED,
    DISABLED,
    UNKNOWN
}

public class RestrictedAreas extends Module {
    private final Map<String, Boolean> playerInArea = new HashMap<>();
    private final Queue<String> commandBuffer = new LinkedList<>();
    private long lastCommandTime = 0;
    private RequestState requestState = RequestState.UNKNOWN;

    // Accept command tracking
    private String pendingAcceptCommand = null;
    private long pendingAcceptTime = 0;
    private boolean cancelAccept = false;

    private final SettingGroup sgHud = settings.createGroup("HUD");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgTitle = settings.createGroup("Title");
    private final SettingGroup sgTeleport = settings.createGroup("Teleport Requests");

    private final Setting<Boolean> showHud = sgHud.add(new BoolSetting.Builder()
        .name("show-hud")
        .description("Show the HUD element displaying the current restricted area.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> hudScale = sgHud.add(new DoubleSetting.Builder()
        .name("hud-scale")
        .description("Scale of the HUD text.")
        .defaultValue(1.0)
        .min(0.5)
        .max(3.0)
        .sliderMin(0.5)
        .sliderMax(3.0)
        .visible(showHud::get)
        .build()
    );

    private final Setting<Double> hudOpacity = sgHud.add(new DoubleSetting.Builder()
        .name("hud-opacity")
        .description("Opacity of the HUD background.")
        .defaultValue(0.4)
        .min(0.0)
        .max(1.0)
        .sliderMin(0.0)
        .sliderMax(1.0)
        .visible(showHud::get)
        .build()
    );

    private final Setting<Boolean> renderArea = sgRender.add(new BoolSetting.Builder()
        .name("render-area")
        .description("Render the restricted area boundaries.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Color of the area boundary lines.")
        .defaultValue(new SettingColor(218, 165, 32, 255))
        .visible(renderArea::get)
        .build()
    );

    private final Setting<SettingColor> boundaryColor = sgRender.add(new ColorSetting.Builder()
        .name("boundary-color")
        .description("Color of the area boundary faces.")
        .defaultValue(new SettingColor(255, 180, 0, 80))
        .visible(renderArea::get)
        .build()
    );

    private final Setting<RenderStyle> renderStyle = sgRender.add(new meteordevelopment.meteorclient.settings.EnumSetting.Builder<RenderStyle>()
        .name("render-style")
        .description("Visual style for rendering area boundaries.")
        .defaultValue(RenderStyle.Solid)
        .visible(renderArea::get)
        .build()
    );

    private final Setting<Boolean> showTitle = sgTitle.add(new BoolSetting.Builder()
        .name("show-title")
        .description("Show title when entering or exiting a restricted area.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoAcceptTp = sgTeleport.add(new BoolSetting.Builder()
        .name("auto-accept-tp")
        .description("Auto accept teleport requests from authorized players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> acceptCommand = sgTeleport.add(new StringSetting.Builder()
        .name("accept-command")
        .description("Command to accept teleport requests.")
        .defaultValue("/tpy")
        .visible(autoAcceptTp::get)
        .build()
    );

    private final Setting<Boolean> autoDenyTp = sgTeleport.add(new BoolSetting.Builder()
        .name("auto-deny-tp")
        .description("Auto deny teleport requests from non-authorized players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> denyCommand = sgTeleport.add(new StringSetting.Builder()
        .name("deny-command")
        .description("Command to deny teleport requests.")
        .defaultValue("/tpn")
        .visible(autoDenyTp::get)
        .build()
    );

    private final Setting<Boolean> autoToggleTp = sgTeleport.add(new BoolSetting.Builder()
        .name("auto-toggle-tp")
        .description("Auto toggle teleport requests when entering areas with no players authorized.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> toggleCommand = sgTeleport.add(new StringSetting.Builder()
        .name("toggle-command")
        .description("Command to toggle teleport requests.")
        .defaultValue("/tpt")
        .visible(autoToggleTp::get)
        .build()
    );

    private final Setting<String> toggleMessage = sgTeleport.add(new StringSetting.Builder()
        .name("toggle-message")
        .description("Expected message from server when running toggle command. Use (enabled/disabled) as placeholder.")
        .defaultValue("Requests are now (enabled/disabled)!")
        .visible(autoToggleTp::get)
        .build()
    );

    public RestrictedAreas() {
        super(Main.CATEGORY, "restricted-areas", "Visualizes restricted areas with boxes.");
    }

    @Override
    public void onActivate() {
        playerInArea.clear();
    }

    @Override
    public void onDeactivate() {
        playerInArea.clear();
        commandBuffer.clear();
        requestState = RequestState.UNKNOWN;
    }

    public boolean shouldShowHud() {
        return isActive() && showHud.get();
    }

    public double getHudScale() {
        return hudScale.get();
    }

    public double getHudOpacity() {
        return hudOpacity.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Handle pending accept commands with 10 second delay
        if (pendingAcceptCommand != null && System.currentTimeMillis() - pendingAcceptTime >= 10000) {
            if (!cancelAccept) {
                queueCommand(pendingAcceptCommand);
            }
            pendingAcceptCommand = null;
            cancelAccept = false;
        }

        // Process command buffer with 1 second delay
        if (!autoToggleTp.get() || commandBuffer.isEmpty() || mc.player == null) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCommandTime >= 1000) {
            String command = commandBuffer.poll();
            sendCommand(command);
            lastCommandTime = currentTime;
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!autoToggleTp.get() && !autoDenyTp.get() && !autoAcceptTp.get()) return;

        String message = event.getMessage().getString();
        String toggleMsg = toggleMessage.get();

        // Handle toggle command response messages
        if (autoToggleTp.get()) {
            // Check for enabled state
            String enabledPattern = toggleMsg.replace("(enabled/disabled)", "enabled");
            if (message.contains(enabledPattern)) {
                requestState = RequestState.ENABLED;
                validateAndFixToggleState();
                return;
            }

            // Check for disabled state
            String disabledPattern = toggleMsg.replace("(enabled/disabled)", "disabled");
            if (message.contains(disabledPattern)) {
                requestState = RequestState.DISABLED;
                validateAndFixToggleState();
                return;
            }
        }

        // Handle teleport accept command detection
        if (autoAcceptTp.get()) {
            String acceptCmd = acceptCommand.get();
            if (acceptCmd != null && !acceptCmd.isEmpty() && mc.player != null) {
                // Extract the base command (e.g., "/tpy" from "/tpy Damix2131")
                String baseCommand = acceptCmd.split(" ")[0];
                if (baseCommand.startsWith("/")) {
                    baseCommand = baseCommand.substring(1);
                }

                // More robust detection - look for the command as a separate word
                // Check multiple patterns to find the command
                String[] words = message.split(" ");
                String playerName = null;

                for (int i = 0; i < words.length; i++) {
                    String word = words[i];
                    // Remove formatting codes and special characters to clean the word
                    String cleanWord = word.replaceAll("§.", "").replaceAll("[^a-zA-Z0-9_]", "");

                    if (cleanWord.equals(baseCommand) && i + 1 < words.length) {
                        // Found the command, next word should be player name
                        playerName = words[i + 1].replaceAll("§.", "").replaceAll("[^a-zA-Z0-9_]", "");
                        break;
                    }
                }

                // If we found a player name after the command
                if (playerName != null && !playerName.isEmpty()) {
                    // Get current server and check if player is authorized
                    String serverIp = getServerIp();
                    if (serverIp == null) return;

                    var serverData = RestrictedAreaManager.getServerData(serverIp);
                    if (serverData.isEmpty()) return;

                    // Get current area (player must be in a restricted area)
                    String currentDimension = mc.player.getWorld().getRegistryKey().getValue().toString();
                    for (var area : serverData.get().getRestrictedAreas()) {
                        var coords = area.getCoordinates();
                        if (!coords.getDimension().equals(currentDimension)) continue;

                        int size = area.getArea();
                        double minX = coords.getX() - size;
                        double minY = coords.getY() - size;
                        double minZ = coords.getZ() - size;
                        double maxX = coords.getX() + size;
                        double maxY = coords.getY() + size;
                        double maxZ = coords.getZ() + size;

                        double playerX = mc.player.getX();
                        double playerY = mc.player.getY();
                        double playerZ = mc.player.getZ();

                        boolean isInside = playerX >= minX && playerX <= maxX &&
                            playerY >= minY && playerY <= maxY &&
                            playerZ >= minZ && playerZ <= maxZ;

                        if (isInside && area.getAllowedPlayers().contains(playerName)) {
                            // Player is authorized, schedule accept with 10 second delay
                            pendingAcceptCommand = acceptCmd + " " + playerName;
                            pendingAcceptTime = System.currentTimeMillis();

                            // Send message with information
                            MutableText playerText = Text.literal(playerName).formatted(Formatting.AQUA);
                            MutableText areaText = Text.literal(area.getName()).formatted(Formatting.GOLD);

                            MutableText messageText = Text.literal("");
                            messageText.append(playerText);
                            messageText.append(Text.literal(" is allowed in "));
                            messageText.append(areaText);
                            messageText.append(Text.literal(". Teleport request will be "));
                            messageText.append(Text.literal("automatically accepted").formatted(Formatting.GREEN, Formatting.BOLD));
                            messageText.append(Text.literal(" in 10 seconds. Run "));
                            messageText.append(Text.literal(".ra cancel").formatted(Formatting.RED, Formatting.BOLD));
                            messageText.append(Text.literal(" to cancel it."));

                            mc.player.sendMessage(messageText, false);
                            return;
                        }
                    }
                }
            }
        }

        // Handle teleport request detection and auto-deny
        if (autoDenyTp.get()) {
            String denyCmd = denyCommand.get();
            if (denyCmd != null && !denyCmd.isEmpty() && mc.player != null) {
                // Extract the base command (e.g., "/tpn" from "/tpn Damix2131")
                String baseCommand = denyCmd.split(" ")[0];
                if (baseCommand.startsWith("/")) {
                    baseCommand = baseCommand.substring(1);
                }

                // More robust detection - look for the command as a separate word
                // Check multiple patterns to find the command
                String[] words = message.split(" ");
                String playerName = null;

                for (int i = 0; i < words.length; i++) {
                    String word = words[i];
                    // Remove formatting codes and special characters to clean the word
                    String cleanWord = word.replaceAll("§.", "").replaceAll("[^a-zA-Z0-9_]", "");

                    if (cleanWord.equals(baseCommand) && i + 1 < words.length) {
                        // Found the command, next word should be player name
                        playerName = words[i + 1].replaceAll("§.", "").replaceAll("[^a-zA-Z0-9_]", "");
                        break;
                    }
                }

                // If we found a player name after the command
                if (playerName != null && !playerName.isEmpty()) {
                    // Get current server and check if player is authorized
                    String serverIp = getServerIp();
                    if (serverIp == null) return;

                    var serverData = RestrictedAreaManager.getServerData(serverIp);
                    if (serverData.isEmpty()) return;

                    // Get current area (player must be in a restricted area)
                    String currentDimension = mc.player.getWorld().getRegistryKey().getValue().toString();
                    for (var area : serverData.get().getRestrictedAreas()) {
                        var coords = area.getCoordinates();
                        if (!coords.getDimension().equals(currentDimension)) continue;

                        int size = area.getArea();
                        double minX = coords.getX() - size;
                        double minY = coords.getY() - size;
                        double minZ = coords.getZ() - size;
                        double maxX = coords.getX() + size;
                        double maxY = coords.getY() + size;
                        double maxZ = coords.getZ() + size;

                        double playerX = mc.player.getX();
                        double playerY = mc.player.getY();
                        double playerZ = mc.player.getZ();

                        boolean isInside = playerX >= minX && playerX <= maxX &&
                            playerY >= minY && playerY <= maxY &&
                            playerZ >= minZ && playerZ <= maxZ;

                        if (isInside && !area.getAllowedPlayers().contains(playerName)) {
                            // Player is not authorized, deny the teleport
                            String denyCommandFull = denyCmd + " " + playerName;
                            queueCommand(denyCommandFull);
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates current toggle state and fixes it if needed.
     * Rules:
     * - If player is inside a restricted area with NO allowed players: requests MUST be DISABLED
     * - Otherwise: requests MUST be ENABLED
     */
    public void validateAndFixToggleState() {
        if (!autoToggleTp.get() || mc.player == null) return;

        String serverIp = getServerIp();
        if (serverIp == null) return;

        var serverData = RestrictedAreaManager.getServerData(serverIp);
        if (serverData.isEmpty()) return;

        String currentDimension = mc.player.getWorld().getRegistryKey().getValue().toString();
        double playerX = mc.player.getX();
        double playerY = mc.player.getY();
        double playerZ = mc.player.getZ();

        // Check if player is in ANY restricted area with no allowed players
        boolean isInRestrictedAreaWithNoPlayers = false;
        for (var area : serverData.get().getRestrictedAreas()) {
            var coords = area.getCoordinates();
            if (!coords.getDimension().equals(currentDimension)) continue;

            int size = area.getArea();
            double minX = coords.getX() - size;
            double minY = coords.getY() - size;
            double minZ = coords.getZ() - size;
            double maxX = coords.getX() + size;
            double maxY = coords.getY() + size;
            double maxZ = coords.getZ() + size;

            boolean isInside = playerX >= minX && playerX <= maxX &&
                playerY >= minY && playerY <= maxY &&
                playerZ >= minZ && playerZ <= maxZ;

            // Check if this area has NO allowed players
            if (isInside && area.getAllowedPlayers().isEmpty()) {
                isInRestrictedAreaWithNoPlayers = true;
                break;
            }
        }

        // Determine target state
        RequestState targetState = isInRestrictedAreaWithNoPlayers ? RequestState.DISABLED : RequestState.ENABLED;

        // If state is incorrect, queue toggle command but remove redundant ones first
        if (requestState != targetState) {
            // Clean up redundant toggle commands in the buffer
            removeRedundantToggleCommands();

            String command = toggleCommand.get();
            if (command != null && !command.isEmpty()) {
                queueCommand(command);
            }
        }
    }

    /**
     * Removes redundant toggle commands from the buffer.
     * Since toggle commands alternate state, we only need ONE toggle command pending at a time.
     * This prevents spam when entering/exiting rapidly.
     */
    private void removeRedundantToggleCommands() {
        String toggleCmd = toggleCommand.get();
        if (toggleCmd == null || toggleCmd.isEmpty()) return;

        // Extract base command without arguments
        String baseToggleCmd = toggleCmd.split(" ")[0];
        if (baseToggleCmd.startsWith("/")) {
            baseToggleCmd = baseToggleCmd.substring(1);
        }

        // Count and remove all toggle commands from buffer (keep only pending operations)
        final String finalBaseToggleCmd = baseToggleCmd;
        int toggleCount = 0;
        for (String cmd : commandBuffer) {
            String baseCmdInBuffer = cmd.split(" ")[0];
            if (baseCmdInBuffer.startsWith("/")) {
                baseCmdInBuffer = baseCmdInBuffer.substring(1);
            }
            if (baseCmdInBuffer.equals(finalBaseToggleCmd)) {
                toggleCount++;
            }
        }

        // If there are multiple toggle commands, remove all of them
        // We'll add exactly ONE new one in validateAndFixToggleState
        if (toggleCount > 0) {
            commandBuffer.removeIf(cmd -> {
                String baseCmdInBuffer = cmd.split(" ")[0];
                if (baseCmdInBuffer.startsWith("/")) {
                    baseCmdInBuffer = baseCmdInBuffer.substring(1);
                }
                return baseCmdInBuffer.equals(finalBaseToggleCmd);
            });
        }
    }

    private void sendCommand(String command) {
        if (mc.player == null) return;
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        mc.player.networkHandler.sendChatCommand(command);
    }

    private void queueCommand(String command) {
        commandBuffer.add(command);
    }

    public String cancelPendingAccept() {
        if (pendingAcceptCommand != null) {
            cancelAccept = true;
            // Extract player name from command (e.g., "/tpy PlayerName" -> "PlayerName")
            String[] parts = pendingAcceptCommand.split(" ");
            String playerName = parts.length > 1 ? parts[parts.length - 1] : null;
            pendingAcceptCommand = null;
            return playerName;
        }
        return null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        String serverIp = getServerIp();
        if (serverIp == null) return;

        var serverData = RestrictedAreaManager.getServerData(serverIp);
        if (serverData.isEmpty()) return;

        String currentDimension = mc.player.getWorld().getRegistryKey().getValue().toString();

        for (var area : serverData.get().getRestrictedAreas()) {
            var coords = area.getCoordinates();

            if (!coords.getDimension().equals(currentDimension)) continue;

            int size = area.getArea();
            double minX = coords.getX() - size;
            double minY = coords.getY() - size;
            double minZ = coords.getZ() - size;
            double maxX = coords.getX() + size;
            double maxY = coords.getY() + size;
            double maxZ = coords.getZ() + size;

            double playerX = mc.player.getX();
            double playerY = mc.player.getY();
            double playerZ = mc.player.getZ();

            boolean isInside = playerX >= minX && playerX <= maxX &&
                playerY >= minY && playerY <= maxY &&
                playerZ >= minZ && playerZ <= maxZ;

            boolean wasInside = playerInArea.getOrDefault(area.getName(), false);

            if (isInside != wasInside) {
                if (showTitle.get()) {
                    mc.inGameHud.setTitleTicks(5, 20, 5);
                    if (isInside) {
                        mc.inGameHud.setTitle(Text.literal(area.getName()).formatted(Formatting.GOLD));
                    } else {
                        mc.inGameHud.setTitle(Text.literal("Wilderness").formatted(Formatting.DARK_GREEN));
                    }
                }

                // Handle toggle command for areas with no authorized players
                // Only queue if we don't already have a toggle command pending
                if (autoToggleTp.get() && area.getAllowedPlayers().isEmpty()) {
                    // Check if there's already a toggle command in the buffer
                    String toggleCmd = toggleCommand.get();
                    if (toggleCmd != null && !toggleCmd.isEmpty()) {
                        String baseToggleCmd = toggleCmd.split(" ")[0];
                        if (baseToggleCmd.startsWith("/")) {
                            baseToggleCmd = baseToggleCmd.substring(1);
                        }

                        final String finalBaseToggleCmd = baseToggleCmd;
                        boolean hasToggleInBuffer = commandBuffer.stream().anyMatch(cmd -> {
                            String baseCmdInBuffer = cmd.split(" ")[0];
                            if (baseCmdInBuffer.startsWith("/")) {
                                baseCmdInBuffer = baseCmdInBuffer.substring(1);
                            }
                            return baseCmdInBuffer.equals(finalBaseToggleCmd);
                        });

                        // Only queue if there's no toggle already pending
                        if (!hasToggleInBuffer) {
                            queueCommand(toggleCmd);
                        }
                    }
                }

                playerInArea.put(area.getName(), isInside);
            }

            if (!renderArea.get()) continue;

            // Apply render style effect
            double alphaMultiplier = 1.0;
            if (renderStyle.get() == RenderStyle.Pulsing) {
                // Pulsing effect using system time
                double pulseSpeed = 2.0;
                alphaMultiplier = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1000.0 * pulseSpeed);
            }

            Color lineCol = new Color(
                lineColor.get().r,
                lineColor.get().g,
                lineColor.get().b,
                (int)(lineColor.get().a * alphaMultiplier)
            );
            Color faceCol = new Color(
                boundaryColor.get().r,
                boundaryColor.get().g,
                boundaryColor.get().b,
                (int)(boundaryColor.get().a * alphaMultiplier)
            );

            event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ, lineCol, lineCol, ShapeMode.Lines, 2);

            double threshold = 3.0;
            double renderSize = 4.0;

            double distToMinX = Math.abs(playerX - minX);
            double distToMaxX = Math.abs(playerX - maxX);
            double distToMinY = Math.abs(playerY - minY);
            double distToMaxY = Math.abs(playerY - maxY);
            double distToMinZ = Math.abs(playerZ - minZ);
            double distToMaxZ = Math.abs(playerZ - maxZ);

            boolean inXRange = playerX >= minX - threshold && playerX <= maxX + threshold;
            boolean inYRange = playerY >= minY - threshold && playerY <= maxY + threshold;
            boolean inZRange = playerZ >= minZ - threshold && playerZ <= maxZ + threshold;

            if (distToMinX < threshold && inYRange && inZRange) {
                double localMinY = Math.max(minY, playerY - renderSize);
                double localMaxY = Math.min(maxY, playerY + renderSize);
                double localMinZ = Math.max(minZ, playerZ - renderSize);
                double localMaxZ = Math.min(maxZ, playerZ + renderSize);
                event.renderer.quadVertical(minX, localMinY, localMinZ, minX, localMaxY, localMaxZ, faceCol);
            }
            if (distToMaxX < threshold && inYRange && inZRange) {
                double localMinY = Math.max(minY, playerY - renderSize);
                double localMaxY = Math.min(maxY, playerY + renderSize);
                double localMinZ = Math.max(minZ, playerZ - renderSize);
                double localMaxZ = Math.min(maxZ, playerZ + renderSize);
                event.renderer.quadVertical(maxX, localMinY, localMinZ, maxX, localMaxY, localMaxZ, faceCol);
            }
            if (distToMinZ < threshold && inXRange && inYRange) {
                double localMinX = Math.max(minX, playerX - renderSize);
                double localMaxX = Math.min(maxX, playerX + renderSize);
                double localMinY = Math.max(minY, playerY - renderSize);
                double localMaxY = Math.min(maxY, playerY + renderSize);
                event.renderer.quadVertical(localMinX, localMinY, minZ, localMaxX, localMaxY, minZ, faceCol);
            }
            if (distToMaxZ < threshold && inXRange && inYRange) {
                double localMinX = Math.max(minX, playerX - renderSize);
                double localMaxX = Math.min(maxX, playerX + renderSize);
                double localMinY = Math.max(minY, playerY - renderSize);
                double localMaxY = Math.min(maxY, playerY + renderSize);
                event.renderer.quadVertical(localMinX, localMinY, maxZ, localMaxX, localMaxY, maxZ, faceCol);
            }
            if (distToMinY < threshold && inXRange && inZRange) {
                double localMinX = Math.max(minX, playerX - renderSize);
                double localMaxX = Math.min(maxX, playerX + renderSize);
                double localMinZ = Math.max(minZ, playerZ - renderSize);
                double localMaxZ = Math.min(maxZ, playerZ + renderSize);
                event.renderer.quadHorizontal(localMinX, minY, localMinZ, localMaxX, localMaxZ, faceCol);
            }
            if (distToMaxY < threshold && inXRange && inZRange) {
                double localMinX = Math.max(minX, playerX - renderSize);
                double localMaxX = Math.min(maxX, playerX + renderSize);
                double localMinZ = Math.max(minZ, playerZ - renderSize);
                double localMaxZ = Math.min(maxZ, playerZ + renderSize);
                event.renderer.quadHorizontal(localMinX, maxY, localMinZ, localMaxX, localMaxZ, faceCol);
            }
        }
    }

    private String getServerIp() {
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
