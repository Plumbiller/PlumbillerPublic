package com.Plumbiller.publicaddon;

import com.Plumbiller.publicaddon.commands.restrictedarea;
import com.Plumbiller.publicaddon.modules.AutoRename;
import com.Plumbiller.publicaddon.util.FileManager;
import com.Plumbiller.publicaddon.util.RestrictedAreaManager;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Main extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("PlumbillerPublic");
    public static final HudGroup HUD_GROUP = new HudGroup("PlumbillerPublic");

    @Override
    public void onInitialize() {
        LOG.info("Initializing PlumbillerPublic addon");

        FileManager.initialize();
        RestrictedAreaManager.load();

        Modules.get().add(new AutoRename());

        Commands.add(new restrictedarea());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.Plumbiller.publicaddon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Plumbiller", "public-addon");
    }
}
