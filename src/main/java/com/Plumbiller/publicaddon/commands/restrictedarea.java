package com.Plumbiller.publicaddon.commands;

import com.Plumbiller.publicaddon.commands.arguments.AllowedPlayerArgumentType;
import com.Plumbiller.publicaddon.commands.arguments.RestrictedAreaArgumentType;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager.Coordinates;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager.RestrictedArea;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.PlayerListEntryArgumentType;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.command.CommandSource;

public class restrictedarea extends Command {

    public restrictedarea() {
        super("restrictedarea", "Manage restricted areas on servers.", "ra");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("create")
                .then(argument("name", StringArgumentType.word())
                        .executes(context -> {
                            String name = context.getArgument("name", String.class);
                            return createArea(name, 100);
                        })
                        .then(argument("area", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    String name = context.getArgument("name", String.class);
                                    int area = context.getArgument("area", Integer.class);
                                    return createArea(name, area);
                                })
                        )
                )
        );

        builder.then(literal("delete")
                .then(argument("area", RestrictedAreaArgumentType.create())
                        .executes(context -> {
                            String name = RestrictedAreaArgumentType.get(context);
                            return deleteArea(name);
                        })
                )
        );

        builder.then(literal("allow")
                .then(argument("area", RestrictedAreaArgumentType.create())
                        .then(argument("player", PlayerListEntryArgumentType.create())
                                .executes(context -> {
                                    String area = RestrictedAreaArgumentType.get(context);
                                    String player = PlayerListEntryArgumentType.get(context).getProfile().getName();
                                    return allowPlayer(player, area);
                                })
                        )
                )
        );

        builder.then(literal("revoke")
                .then(argument("area", RestrictedAreaArgumentType.create())
                        .then(argument("player", AllowedPlayerArgumentType.create())
                                .executes(context -> {
                                    String area = RestrictedAreaArgumentType.get(context);
                                    String player = AllowedPlayerArgumentType.get(context);
                                    return revokePlayer(player, area);
                                })
                        )
                )
        );

        builder.then(literal("list")
                .executes(context -> {
                    return listAreas();
                })
        );

        builder.then(literal("players")
                .then(argument("area", RestrictedAreaArgumentType.create())
                        .executes(context -> {
                            String area = RestrictedAreaArgumentType.get(context);
                            return listPlayers(area);
                        })
                )
        );
    }

    private int createArea(String name, int area) {
        if (mc.player == null) {
            error("You must be in-game to use this command.");
            return SINGLE_SUCCESS;
        }

        String serverIp = getServerIp();
        if (serverIp == null) {
            error("Could not determine server IP.");
            return SINGLE_SUCCESS;
        }

        String dimension = mc.player.getWorld().getRegistryKey().getValue().toString();

        Coordinates coords = new Coordinates(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                dimension
        );

        RestrictedArea restrictedArea = new RestrictedArea(name, coords, area);

        if (RestrictedAreaManager.createRestrictedArea(serverIp, restrictedArea)) {
            info("§fCreated restricted area §6%s§f at §6%s§f with area size ±§6%d§f.",
                    name, coords.toString(), area);
        } else {
            error("Restricted area §c%s§f already exists.", name);
        }

        return SINGLE_SUCCESS;
    }

    private int deleteArea(String name) {
        String serverIp = getServerIp();
        if (serverIp == null) {
            error("Could not determine server IP.");
            return SINGLE_SUCCESS;
        }

        if (RestrictedAreaManager.deleteRestrictedArea(serverIp, name)) {
            info("§fDeleted restricted area §6%s§f.", name);
        } else {
            error("Restricted area §c%s§f does not exist.", name);
        }

        return SINGLE_SUCCESS;
    }

    private int allowPlayer(String playerName, String areaName) {
        String serverIp = getServerIp();
        if (serverIp == null) {
            error("Could not determine server IP.");
            return SINGLE_SUCCESS;
        }

        var area = RestrictedAreaManager.getRestrictedArea(serverIp, areaName);
        if (area.isEmpty()) {
            error("Could not add player. Area §c%s§f may not exist.", areaName);
            return SINGLE_SUCCESS;
        }

        if (area.get().isPlayerAllowed(playerName)) {
            info("§fPlayer §b%s§f is already allowed in area §6%s§f.", playerName, areaName);
            return SINGLE_SUCCESS;
        }

        if (RestrictedAreaManager.allowPlayer(serverIp, areaName, playerName)) {
            info("§fAllowed §b%s§f for area §6%s§f.", playerName, areaName);
        } else {
            error("Could not add player. Area §c%s§f or player §b%s§f may not exist.", areaName, playerName);
        }

        return SINGLE_SUCCESS;
    }

    private int revokePlayer(String playerName, String areaName) {
        String serverIp = getServerIp();
        if (serverIp == null) {
            error("Could not determine server IP.");
            return SINGLE_SUCCESS;
        }

        if (RestrictedAreaManager.revokePlayer(serverIp, areaName, playerName)) {
            info("§fRevoked access to §b%s§f from §6%s§f.", playerName, areaName);
        } else {
            error("Could not remove player. Area or player may not exist.");
        }

        return SINGLE_SUCCESS;
    }

    private int listAreas() {
        String serverIp = getServerIp();
        if (serverIp == null) {
            error("Could not determine server IP.");
            return SINGLE_SUCCESS;
        }

        var serverData = RestrictedAreaManager.getServerData(serverIp);
        if (serverData.isEmpty() || serverData.get().getRestrictedAreas().isEmpty()) {
            info("No restricted areas found on this server.");
            return SINGLE_SUCCESS;
        }

        info("§fRestricted Areas:", serverData.get().getRestrictedAreas().size());
        for (var area : serverData.get().getRestrictedAreas()) {
            info("§6%s§f", area.getName());
        }

        return SINGLE_SUCCESS;
    }

    private int listPlayers(String areaName) {
        String serverIp = getServerIp();
        if (serverIp == null) {
            error("Could not determine server IP.");
            return SINGLE_SUCCESS;
        }

        var area = RestrictedAreaManager.getRestrictedArea(serverIp, areaName);
        if (area.isEmpty()) {
            error("§fRestricted area §c%s§f does not exist.", areaName);
            return SINGLE_SUCCESS;
        }

        if (area.get().getAllowedPlayers().isEmpty()) {
            info("§fNo players allowed in area §6%s§f.", areaName);
            return SINGLE_SUCCESS;
        }

        info("§f-Allowed Players in §6%s§f", areaName, area.get().getAllowedPlayers().size());
        for (String player : area.get().getAllowedPlayers()) {
            info("§b%s§f", player);
        }

        return SINGLE_SUCCESS;
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
