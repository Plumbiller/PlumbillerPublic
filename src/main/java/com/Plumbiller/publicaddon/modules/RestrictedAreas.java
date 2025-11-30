package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.Main;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

public class RestrictedAreas extends Module {
    private final Map<String, Boolean> playerInArea = new HashMap<>();

    public RestrictedAreas() {
        super(Main.CATEGORY, "restricted-areas", "Visualizes restricted areas with boxes.");
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
                mc.inGameHud.setTitleTicks(5, 20, 5);
                if (isInside) {
                    mc.inGameHud.setTitle(Text.literal(area.getName()).formatted(Formatting.GOLD));
                } else {
                    mc.inGameHud.setTitle(Text.literal("Wilderness").formatted(Formatting.DARK_GREEN));
                }
                playerInArea.put(area.getName(), isInside);
            }

            Color lineColor = new Color(218, 165, 32, 255);
            Color faceColor = new Color(255, 180, 0, 80);

            event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ, lineColor, lineColor, ShapeMode.Lines, 2);

            double threshold = 2.0;
            double renderSize = 3.0;

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
                event.renderer.quadVertical(minX, localMinY, localMinZ, minX, localMaxY, localMaxZ, faceColor);
            }
            if (distToMaxX < threshold && inYRange && inZRange) {
                double localMinY = Math.max(minY, playerY - renderSize);
                double localMaxY = Math.min(maxY, playerY + renderSize);
                double localMinZ = Math.max(minZ, playerZ - renderSize);
                double localMaxZ = Math.min(maxZ, playerZ + renderSize);
                event.renderer.quadVertical(maxX, localMinY, localMinZ, maxX, localMaxY, localMaxZ, faceColor);
            }
            if (distToMinZ < threshold && inXRange && inYRange) {
                double localMinX = Math.max(minX, playerX - renderSize);
                double localMaxX = Math.min(maxX, playerX + renderSize);
                double localMinY = Math.max(minY, playerY - renderSize);
                double localMaxY = Math.min(maxY, playerY + renderSize);
                event.renderer.quadVertical(localMinX, localMinY, minZ, localMaxX, localMaxY, minZ, faceColor);
            }
            if (distToMaxZ < threshold && inXRange && inYRange) {
                double localMinX = Math.max(minX, playerX - renderSize);
                double localMaxX = Math.min(maxX, playerX + renderSize);
                double localMinY = Math.max(minY, playerY - renderSize);
                double localMaxY = Math.min(maxY, playerY + renderSize);
                event.renderer.quadVertical(localMinX, localMinY, maxZ, localMaxX, localMaxY, maxZ, faceColor);
            }
            if (distToMinY < threshold && inXRange && inZRange) {
                double localMinX = Math.max(minX, playerX - renderSize);
                double localMaxX = Math.min(maxX, playerX + renderSize);
                double localMinZ = Math.max(minZ, playerZ - renderSize);
                double localMaxZ = Math.min(maxZ, playerZ + renderSize);
                event.renderer.quadHorizontal(localMinX, minY, localMinZ, localMaxX, localMaxZ, faceColor);
            }
            if (distToMaxY < threshold && inXRange && inZRange) {
                double localMinX = Math.max(minX, playerX - renderSize);
                double localMaxX = Math.min(maxX, playerX + renderSize);
                double localMinZ = Math.max(minZ, playerZ - renderSize);
                double localMaxZ = Math.min(maxZ, playerZ + renderSize);
                event.renderer.quadHorizontal(localMinX, maxY, localMinZ, localMaxX, localMaxZ, faceColor);
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
