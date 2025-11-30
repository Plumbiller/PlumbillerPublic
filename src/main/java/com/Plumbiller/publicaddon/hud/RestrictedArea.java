package com.Plumbiller.publicaddon.hud;

import com.Plumbiller.publicaddon.Main;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

public class RestrictedArea extends HudElement {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static final HudElementInfo<RestrictedArea> INFO = new HudElementInfo<>(
            Main.HUD_GROUP,
            "restricted-area",
            "Displays the name of the restricted area you are currently in.",
            RestrictedArea::new
    );

    public RestrictedArea() {
        super(INFO);
        this.x = 5;
        this.y = 190;
    }

    @Override
    public void render(HudRenderer renderer) {
        com.Plumbiller.publicaddon.modules.RestrictedAreas module = Modules.get().get(com.Plumbiller.publicaddon.modules.RestrictedAreas.class);

        if (module == null || !module.isActive() || mc.player == null || mc.world == null) {
            return;
        }

        String serverIp = getServerIp();
        if (serverIp == null) {
            return;
        }

        var serverData = RestrictedAreaManager.getServerData(serverIp);
        if (serverData.isEmpty()) {
            return;
        }

        String currentDimension = mc.player.getWorld().getRegistryKey().getValue().toString();
        double playerX = mc.player.getX();
        double playerY = mc.player.getY();
        double playerZ = mc.player.getZ();

        RestrictedAreaManager.RestrictedArea currentArea = null;

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

            if (isInside) {
                currentArea = area;
                break;
            }
        }

        if (currentArea == null) {
            return;
        }

        String areaName = currentArea.getName();
        int allowedPlayersCount = currentArea.getAllowedPlayers().size();
        String countText = " (" + allowedPlayersCount + ")";

        double scale = 1;
        boolean useCustomFont = false;

        double nameWidth = renderer.textWidth(areaName, useCustomFont) * scale;
        double countWidth = renderer.textWidth(countText, useCustomFont) * scale;
        double totalWidth = nameWidth + countWidth;
        double textHeight = renderer.textHeight(useCustomFont) * scale;

        renderer.quad(x, y, totalWidth, textHeight, new Color(0, 0, 0, 40));

        renderer.text(areaName, x, y, new Color(255, 215, 0), useCustomFont, scale);
        renderer.text(countText, x + nameWidth, y, new Color(0, 255, 255), useCustomFont, scale);

        setSize(totalWidth, textHeight);
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
