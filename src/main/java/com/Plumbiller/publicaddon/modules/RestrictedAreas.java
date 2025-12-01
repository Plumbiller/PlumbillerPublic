package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.Main;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
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
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

enum RenderStyle {
    Solid,
    Pulsing
}

public class RestrictedAreas extends Module {
    private final Map<String, Boolean> playerInArea = new HashMap<>();

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

    public RestrictedAreas() {
        super(Main.CATEGORY, "restricted-areas", "Visualizes restricted areas with boxes.");
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
