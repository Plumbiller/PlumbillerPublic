package com.Plumbiller.publicaddon.util;

import com.Plumbiller.publicaddon.Main;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileManager {
    private static final String ADDON_FOLDER_NAME = "PlumbillerAddon";
    private static final String RESTRICTED_AREAS_FILE = "restricted_areas.json";

    private static Path addonFolder;
    private static Path restrictedAreasFile;

    public static void initialize() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            addonFolder = gameDir.resolve(ADDON_FOLDER_NAME);

            if (!Files.exists(addonFolder)) {
                Files.createDirectories(addonFolder);
                Main.LOG.info("Addon folder created at: {}", addonFolder.toAbsolutePath());
            }

            restrictedAreasFile = addonFolder.resolve(RESTRICTED_AREAS_FILE);

            if (!Files.exists(restrictedAreasFile)) {
                Files.createFile(restrictedAreasFile);
                Files.writeString(restrictedAreasFile, "[]");
                Main.LOG.info("Restricted areas file created: {}", restrictedAreasFile.toAbsolutePath());
            }

            Main.LOG.info("FileManager initialized successfully");

        } catch (IOException e) {
            Main.LOG.error("Error initializing FileManager", e);
        }
    }

    public static Path getAddonFolder() {
        if (addonFolder == null) {
            initialize();
        }
        return addonFolder;
    }

    public static Path getRestrictedAreasFile() {
        if (restrictedAreasFile == null) {
            initialize();
        }
        return restrictedAreasFile;
    }

    public static String readRestrictedAreas() {
        try {
            return Files.readString(getRestrictedAreasFile());
        } catch (IOException e) {
            Main.LOG.error("Error reading restricted areas", e);
            return "[]";
        }
    }

    public static void writeRestrictedAreas(String content) {
        try {
            Files.writeString(getRestrictedAreasFile(), content);
            Main.LOG.info("Restricted areas saved successfully");
        } catch (IOException e) {
            Main.LOG.error("Error writing restricted areas", e);
        }
    }
}

