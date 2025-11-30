package com.Plumbiller.publicaddon.util;

import com.Plumbiller.publicaddon.Main;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RestrictedAreaManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeSpecialFloatingPointValues()
            .create();
    private static List<ServerData> serverDataList = new ArrayList<>();

    public static class Coordinates {
        private int x;
        private int y;
        private int z;
        private String dimension;

        public Coordinates() {}

        public Coordinates(double x, double y, double z, String dimension) {
            this.x = (int) Math.floor(x);
            this.y = (int) Math.floor(y);
            this.z = (int) Math.floor(z);
            this.dimension = dimension;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getZ() {
            return z;
        }

        public void setZ(int z) {
            this.z = z;
        }

        public String getDimension() {
            return dimension;
        }

        public void setDimension(String dimension) {
            this.dimension = dimension;
        }

        @Override
        public String toString() {
            return String.format("%d, %d, %d [%s]", x, y, z, getDimensionName());
        }

        private String getDimensionName() {
            if (dimension == null) return "unknown";
            if (dimension.contains("overworld")) return "Overworld";
            if (dimension.contains("the_nether")) return "Nether";
            if (dimension.contains("the_end")) return "End";
            return dimension;
        }
    }

    public static class RestrictedArea {
        private String name;
        private Coordinates coordinates;
        private int area;
        private List<String> allowedPlayers;

        public RestrictedArea() {
            this.allowedPlayers = new ArrayList<>();
        }

        public RestrictedArea(String name, Coordinates coordinates, int area) {
            this.name = name;
            this.coordinates = coordinates;
            this.area = area;
            this.allowedPlayers = new ArrayList<>();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Coordinates getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(Coordinates coordinates) {
            this.coordinates = coordinates;
        }

        public int getArea() {
            return area;
        }

        public void setArea(int area) {
            this.area = area;
        }

        public List<String> getAllowedPlayers() {
            return allowedPlayers;
        }

        public void setAllowedPlayers(List<String> allowedPlayers) {
            this.allowedPlayers = allowedPlayers;
        }

        public boolean addAllowedPlayer(String player) {
            if (!allowedPlayers.contains(player)) {
                allowedPlayers.add(player);
                return true;
            }
            return false;
        }

        public boolean removeAllowedPlayer(String player) {
            return allowedPlayers.remove(player);
        }

        public boolean isPlayerAllowed(String player) {
            return allowedPlayers.contains(player);
        }
    }

    public static class ServerData {
        private String server;
        private List<RestrictedArea> restrictedAreas;

        public ServerData() {
            this.restrictedAreas = new ArrayList<>();
        }

        public ServerData(String server) {
            this.server = server;
            this.restrictedAreas = new ArrayList<>();
        }

        public String getServer() {
            return server;
        }

        public void setServer(String server) {
            this.server = server;
        }

        public List<RestrictedArea> getRestrictedAreas() {
            return restrictedAreas;
        }

        public void setRestrictedAreas(List<RestrictedArea> restrictedAreas) {
            this.restrictedAreas = restrictedAreas;
        }

        public void addRestrictedArea(RestrictedArea area) {
            restrictedAreas.add(area);
        }

        public boolean removeRestrictedArea(String name) {
            return restrictedAreas.removeIf(area -> area.getName().equalsIgnoreCase(name));
        }

        public Optional<RestrictedArea> getRestrictedArea(String name) {
            return restrictedAreas.stream()
                    .filter(area -> area.getName().equalsIgnoreCase(name))
                    .findFirst();
        }

        public boolean hasRestrictedArea(String name) {
            return restrictedAreas.stream()
                    .anyMatch(area -> area.getName().equalsIgnoreCase(name));
        }
    }

    public static void load() {
        try {
            String json = FileManager.readRestrictedAreas();
            Type listType = new TypeToken<ArrayList<ServerData>>(){}.getType();
            List<ServerData> loaded = GSON.fromJson(json, listType);
            if (loaded != null) {
                serverDataList = loaded;
            }
        } catch (Exception e) {
            Main.LOG.error("Error loading restricted areas", e);
            serverDataList = new ArrayList<>();
        }
    }

    public static void save() {
        try {
            String json = GSON.toJson(serverDataList);
            FileManager.writeRestrictedAreas(json);
        } catch (Exception e) {
            Main.LOG.error("Error saving restricted areas", e);
        }
    }

    public static ServerData getOrCreateServerData(String serverIp) {
        Optional<ServerData> existing = serverDataList.stream()
                .filter(sd -> sd.getServer().equals(serverIp))
                .findFirst();

        if (existing.isPresent()) {
            return existing.get();
        }

        ServerData newServer = new ServerData(serverIp);
        serverDataList.add(newServer);
        return newServer;
    }

    public static Optional<ServerData> getServerData(String serverIp) {
        return serverDataList.stream()
                .filter(sd -> sd.getServer().equals(serverIp))
                .findFirst();
    }

    public static boolean createRestrictedArea(String serverIp, RestrictedArea area) {
        ServerData serverData = getOrCreateServerData(serverIp);

        if (serverData.hasRestrictedArea(area.getName())) {
            return false;
        }

        serverData.addRestrictedArea(area);
        save();
        return true;
    }

    public static boolean deleteRestrictedArea(String serverIp, String areaName) {
        Optional<ServerData> serverData = getServerData(serverIp);
        if (serverData.isEmpty()) {
            return false;
        }

        boolean removed = serverData.get().removeRestrictedArea(areaName);
        if (removed) {
            save();
        }
        return removed;
    }

    public static boolean allowPlayer(String serverIp, String areaName, String playerName) {
        Optional<ServerData> serverData = getServerData(serverIp);
        if (serverData.isEmpty()) {
            return false;
        }

        Optional<RestrictedArea> area = serverData.get().getRestrictedArea(areaName);
        if (area.isEmpty()) {
            return false;
        }

        boolean added = area.get().addAllowedPlayer(playerName);
        if (added) {
            save();
        }
        return added;
    }

    public static boolean revokePlayer(String serverIp, String areaName, String playerName) {
        Optional<ServerData> serverData = getServerData(serverIp);
        if (serverData.isEmpty()) {
            return false;
        }

        Optional<RestrictedArea> area = serverData.get().getRestrictedArea(areaName);
        if (area.isEmpty()) {
            return false;
        }

        boolean removed = area.get().removeAllowedPlayer(playerName);
        if (removed) {
            save();
        }
        return removed;
    }

    public static Optional<RestrictedArea> getRestrictedArea(String serverIp, String areaName) {
        Optional<ServerData> serverData = getServerData(serverIp);
        if (serverData.isEmpty()) {
            return Optional.empty();
        }
        return serverData.get().getRestrictedArea(areaName);
    }
}

