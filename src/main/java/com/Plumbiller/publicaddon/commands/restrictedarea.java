package com.Plumbiller.publicaddon.commands;

import com.Plumbiller.publicaddon.commands.arguments.AllowedPlayerArgumentType;
import com.Plumbiller.publicaddon.commands.arguments.RestrictedAreaArgumentType;
import com.Plumbiller.publicaddon.modules.RestrictedAreas;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager.Coordinates;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager.RestrictedArea;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.PlayerListEntryArgumentType;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

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
                                }))));

        builder.then(literal("delete")
                .then(argument("area", RestrictedAreaArgumentType.create())
                        .executes(context -> {
                            String name = RestrictedAreaArgumentType.get(context);
                            return deleteArea(name);
                        })));

        builder.then(literal("allow")
                .then(argument("area", RestrictedAreaArgumentType.create())
                        .then(argument("player", PlayerListEntryArgumentType.create())
                                .executes(context -> {
                                    String area = RestrictedAreaArgumentType.get(context);
                                    String player = PlayerListEntryArgumentType.get(context).getProfile().getName();
                                    return allowPlayer(player, area);
                                }))));

        builder.then(literal("revoke")
                .then(argument("area", RestrictedAreaArgumentType.create())
                        .then(argument("player", AllowedPlayerArgumentType.create())
                                .executes(context -> {
                                    String area = RestrictedAreaArgumentType.get(context);
                                    String player = AllowedPlayerArgumentType.get(context);
                                    return revokePlayer(player, area);
                                }))));

        builder.then(literal("list")
                .executes(context -> {
                    return listAreas();
                }));

        builder.then(literal("players")
                .then(argument("area", RestrictedAreaArgumentType.create())
                        .executes(context -> {
                            String area = RestrictedAreaArgumentType.get(context);
                            return listPlayers(area);
                        })));

        builder.then(literal("cancel")
                .executes(context -> {
                    return cancelPendingAccept();
                }));

        builder.then(literal("modify")
                .then(literal("name")
                        .then(argument("area", RestrictedAreaArgumentType.create())
                                .then(argument("new_name", StringArgumentType.word())
                                        .executes(context -> {
                                            String area = RestrictedAreaArgumentType.get(context);
                                            String newName = context.getArgument("new_name", String.class);
                                            return renameArea(area, newName);
                                        }))))
                .then(literal("area")
                        .then(argument("area", RestrictedAreaArgumentType.create())
                                .then(argument("size", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            String area = RestrictedAreaArgumentType.get(context);
                                            int size = context.getArgument("size", Integer.class);
                                            return resizeArea(area, size);
                                        }))))
                .then(literal("position")
                        .then(argument("area", RestrictedAreaArgumentType.create())
                                .then(argument("pos", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String area = RestrictedAreaArgumentType.get(context);
                                            return repositionArea(context, area);
                                        })))));
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
                dimension);

        RestrictedArea restrictedArea = new RestrictedArea(name, coords, area);

        if (RestrictedAreaManager.createRestrictedArea(serverIp, restrictedArea)) {
            info("§fCreated restricted area §6%s§f at §6%s§f with area size ±§6%d§f.",
                    name, coords.toString(), area);
            RestrictedAreas restrictedAreasModule = Modules.get().get(RestrictedAreas.class);
            if (restrictedAreasModule != null) {
                restrictedAreasModule.validateAndFixToggleState();
            }
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
            RestrictedAreas restrictedAreasModule = Modules.get().get(RestrictedAreas.class);
            if (restrictedAreasModule != null) {
                restrictedAreasModule.validateAndFixToggleState();
            }
        } else {
            error("Could not delete area. Area §c%s§f may not exist.", name);
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
            RestrictedAreas restrictedAreasModule = Modules.get().get(RestrictedAreas.class);
            if (restrictedAreasModule != null) {
                restrictedAreasModule.validateAndFixToggleState();
            }
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
            RestrictedAreas restrictedAreasModule = Modules.get().get(RestrictedAreas.class);
            if (restrictedAreasModule != null) {
                restrictedAreasModule.validateAndFixToggleState();
            }
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

    private int cancelPendingAccept() {
        RestrictedAreas module = Modules.get().get(RestrictedAreas.class);
        if (module != null) {
            String playerName = module.cancelPendingAccept();
            if (playerName != null) {
                if (mc.player != null) {
                    MutableText message = Text.literal("");
                    message.append(Text.literal(playerName).formatted(Formatting.AQUA));
                    message.append(Text.literal(" request will be ignored.").formatted(Formatting.WHITE));
                    mc.player.sendMessage(message, false);
                }
            } else {
                error("No pending teleport accept to cancel.");
            }
        } else {
            error("RestrictedAreas module not found.");
        }
        return SINGLE_SUCCESS;
    }

    private int renameArea(String oldName, String newName) {
        String serverIp = getServerIp();
        if (serverIp == null) {
            error("Could not determine server IP.");
            return SINGLE_SUCCESS;
        }

        if (RestrictedAreaManager.renameRestrictedArea(serverIp, oldName, newName)) {
            info("§frenamed restricted area §6%s§f to §6%s§f.", oldName, newName);
            RestrictedAreas restrictedAreasModule = Modules.get().get(RestrictedAreas.class);
            if (restrictedAreasModule != null) {
                restrictedAreasModule.validateAndFixToggleState();
            }
        } else {
            error("Could not rename restricted area §c%s§f. Name may be taken or area not found.", oldName);
        }

        return SINGLE_SUCCESS;
    }

    private int resizeArea(String name, int newSize) {
        String serverIp = getServerIp();
        if (serverIp == null) {
            error("Could not determine server IP.");
            return SINGLE_SUCCESS;
        }

        if (RestrictedAreaManager.resizeRestrictedArea(serverIp, name, newSize)) {
            info("§fResized restricted area §6%s§f to ±§6%d§f.", name, newSize);
            RestrictedAreas restrictedAreasModule = Modules.get().get(RestrictedAreas.class);
            if (restrictedAreasModule != null) {
                restrictedAreasModule.validateAndFixToggleState();
            }
        } else {
            error("Could not resize restricted area §c%s§f.", name);
        }

        return SINGLE_SUCCESS;
    }

    private int repositionArea(CommandContext<CommandSource> context, String name) throws CommandSyntaxException {
        String serverIp = getServerIp();
        if (serverIp == null) {
            error("Could not determine server IP.");
            return SINGLE_SUCCESS;
        }

        String arg = context.getArgument("pos", String.class);
        String[] parts = arg.trim().split("\\s+");
        if (parts.length != 3) {
            error("Invalid coordinates. Usage: <x> <y> <z> (supports ~)");
            return SINGLE_SUCCESS;
        }

        if (mc.player == null)
            return SINGLE_SUCCESS;

        try {
            double x = parseCoord(parts[0], mc.player.getX());
            double y = parseCoord(parts[1], mc.player.getY());
            double z = parseCoord(parts[2], mc.player.getZ());

            String dimension = mc.player.getWorld().getRegistryKey().getValue().toString();
            Coordinates coords = new Coordinates(x, y, z, dimension);

            if (RestrictedAreaManager.repositionRestrictedArea(serverIp, name, coords)) {
                info("§fRestricted area §6%s§f moved to §6%s§f.", name, coords.toString());
                RestrictedAreas restrictedAreasModule = Modules.get().get(RestrictedAreas.class);
                if (restrictedAreasModule != null) {
                    restrictedAreasModule.validateAndFixToggleState();
                }
            } else {
                error("Could not move restricted area §c%s§f.", name);
            }
        } catch (NumberFormatException e) {
            error("Invalid number format.");
        }

        return SINGLE_SUCCESS;
    }

    private double parseCoord(String input, double current) {
        if (input.startsWith("~")) {
            if (input.length() == 1)
                return current;
            return current + Double.parseDouble(input.substring(1));
        }
        return Double.parseDouble(input);
    }
}
