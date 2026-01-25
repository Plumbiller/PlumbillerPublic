package com.Plumbiller.publicaddon.commands;

import com.Plumbiller.publicaddon.Main;
import com.Plumbiller.publicaddon.util.FileManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.command.CommandSource;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class Panorama extends Command {
    public Panorama() {
        super("panorama", "Takes a panorama and saves it to the addon directory.", "gen-panorama");
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    private int timer = 10;
    private int screenshot = 0;
    private float preYaw = 0f;
    private float prePitch = 0f;
    private int preWidth = 0;
    private int preHeight = 0;

    private boolean isWarming = false;
    private boolean takingPanorama = false;

    @Nullable
    private Path currentPanoramaDir = null;
    @Nullable
    private MinecraftClient instance = null;

    private void takeWarmedScreenshot() {
        MinecraftClient mc = this.instance;
        Path dir = this.currentPanoramaDir;
        if (dir == null || mc == null)
            return;

        String fileName = "panorama_" + screenshot + ".png";
        File file = dir.resolve("screenshots").resolve(fileName).toFile();

        ScreenshotRecorder.saveScreenshot(
                dir.toFile(),
                fileName,
                mc.getFramebuffer(),
                1,
                msg -> {
                    resizeToSquare(file);
                });

        ++screenshot;
        isWarming = false;
    }

    private void resizeToSquare(File file) {
        new Thread(() -> {
            try {

                Thread.sleep(500);

                if (!file.exists()) {
                    Main.LOG.error("Panorama file not found for resizing: " + file.getAbsolutePath());
                    return;
                }

                BufferedImage image = ImageIO.read(file);
                int width = image.getWidth();
                int height = image.getHeight();

                if (width != height) {

                    int side = height;

                    BufferedImage resized = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = resized.createGraphics();

                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                    g.drawImage(image, 0, 0, side, side, null);
                    g.dispose();

                    ImageIO.write(resized, "png", file);
                    Main.LOG.info("Resized panorama screenshot " + file.getName() + " to " + side + "x" + side);
                } else {
                    Main.LOG.info("Panorama screenshot " + file.getName() + " is already square.");
                }
            } catch (Exception e) {
                Main.LOG.error("Failed to resize panorama screenshot", e);
                e.printStackTrace();
            }
        }).start();
    }

    private void startPanoramaProcess(String name) {
        Path panoramaDir = FileManager.getAddonFolder().resolve("panoramas").resolve(name);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null)
            return;

        try {
            Files.createDirectories(panoramaDir);
        } catch (Exception err) {
            error("Failed to create directory: " + err.getMessage());
            return;
        }

        instance = mc;
        screenshot = 0;
        preYaw = mc.player.getYaw();
        prePitch = mc.player.getPitch();
        currentPanoramaDir = panoramaDir;
        preWidth = mc.getWindow().getFramebufferWidth();
        preHeight = mc.getWindow().getFramebufferHeight();

        mc.getWindow().setFramebufferWidth(4096);
        mc.getWindow().setFramebufferHeight(4096);

        takingPanorama = true;
        info("Starting panorama capture. Please wait...");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(
                argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = ctx.getArgument("name", String.class);
                    startPanoramaProcess(name);
                    return SINGLE_SUCCESS;
                }));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        MinecraftClient mc = this.instance;
        Path dir = this.currentPanoramaDir;
        if (mc == null || mc.player == null || dir == null)
            return;

        if (!takingPanorama)
            return;

        if (timer > 0) {
            --timer;
            return;
        } else {

            timer = 10;
        }

        switch (screenshot) {
            case 0 -> {
                if (!isWarming) {
                    mc.gameRenderer.setRenderingPanorama(true);
                    mc.gameRenderer.setBlockOutlineEnabled(false);
                    if (!mc.options.hudHidden)
                        mc.options.hudHidden = true;

                    mc.player.setYaw(preYaw);
                    mc.player.setPitch(0f);
                    isWarming = true;
                } else
                    takeWarmedScreenshot();
            }
            case 1 -> {
                if (!isWarming) {

                    mc.player.setYaw((preYaw + 90f) % 360f);
                    mc.player.setPitch(0f);
                    isWarming = true;
                } else
                    takeWarmedScreenshot();
            }
            case 2 -> {
                if (!isWarming) {

                    mc.player.setYaw((preYaw + 180f) % 360f);
                    mc.player.setPitch(0f);
                    isWarming = true;
                } else
                    takeWarmedScreenshot();
            }
            case 3 -> {
                if (!isWarming) {

                    mc.player.setYaw((preYaw - 90f) % 360f);
                    mc.player.setPitch(0f);
                    isWarming = true;
                } else
                    takeWarmedScreenshot();
            }
            case 4 -> {
                if (!isWarming) {

                    mc.player.setYaw(preYaw);
                    mc.player.setPitch(-90f);
                    isWarming = true;
                } else
                    takeWarmedScreenshot();
            }
            default -> {
                if (!isWarming) {

                    mc.player.setYaw(preYaw);
                    mc.player.setPitch(90f);
                    isWarming = true;
                } else {
                    takeWarmedScreenshot();

                    takingPanorama = false;
                    mc.player.setYaw(preYaw);
                    mc.player.setPitch(prePitch);
                    mc.gameRenderer.setRenderingPanorama(false);
                    mc.gameRenderer.setBlockOutlineEnabled(true);
                    mc.getWindow().setFramebufferWidth(preWidth);
                    mc.getWindow().setFramebufferHeight(preHeight);
                    if (mc.options.hudHidden)
                        mc.options.hudHidden = false;

                    info("Panorama saved to " + currentPanoramaDir.toAbsolutePath());
                }
            }
        }
    }
}
