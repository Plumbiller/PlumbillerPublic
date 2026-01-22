package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.Main;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.network.packet.c2s.common.ClientOptionsC2SPacket;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SkinFlicker extends Module {
    private final SettingGroup sgMode = settings.getDefaultGroup();
    private final SettingGroup sgDelay = settings.createGroup("Delay");
    private final SettingGroup sgMultipleParts = settings.createGroup("Multiple Parts");
    private final SettingGroup sgParts = settings.createGroup("Parts");

    enum FlickerMode {
        HORIZONTAL("Horizontal"),
        VERTICAL("Vertical"),
        RANDOM("Random");

        private final String title;

        FlickerMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    enum HorizontalDirection {
        LEFT_TO_RIGHT("Left -> Right"),
        RIGHT_TO_LEFT("Right -> Left");

        private final String title;

        HorizontalDirection(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    enum VerticalDirection {
        TOP_TO_BOTTOM("Top -> Bottom"),
        BOTTOM_TO_TOP("Bottom -> Top");

        private final String title;

        VerticalDirection(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    enum PartsMode {
        ONE_BY_ONE("One by One"),
        MULTIPLE_PARTS("Multiple Parts"),
        ALL_SIMULTANEOUSLY("All Simultaneously");

        private final String title;

        PartsMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final Setting<FlickerMode> mode = sgMode.add(new EnumSetting.Builder<FlickerMode>()
            .name("mode")
            .description("The flicker mode.")
            .defaultValue(FlickerMode.HORIZONTAL)
            .onChanged(v -> resetFlickerState())
            .build());

    private final Setting<HorizontalDirection> horizontalDirection = sgMode
            .add(new EnumSetting.Builder<HorizontalDirection>()
                    .name("horizontal-direction")
                    .description("Direction of the horizontal flicker.")
                    .defaultValue(HorizontalDirection.LEFT_TO_RIGHT)
                    .visible(() -> mode.get() == FlickerMode.HORIZONTAL)
                    .onChanged(v -> resetFlickerState())
                    .build());

    private final Setting<VerticalDirection> verticalDirection = sgMode.add(new EnumSetting.Builder<VerticalDirection>()
            .name("vertical-direction")
            .description("Direction of the vertical flicker.")
            .defaultValue(VerticalDirection.TOP_TO_BOTTOM)
            .visible(() -> mode.get() == FlickerMode.VERTICAL)
            .onChanged(v -> resetFlickerState())
            .build());

    private final Setting<Boolean> randomizeDelay = sgDelay.add(new BoolSetting.Builder()
            .name("randomize-delay")
            .description("Randomize the delay between toggles.")
            .defaultValue(false)
            .build());

    private final Setting<Integer> delay = sgDelay.add(new IntSetting.Builder()
            .name("delay")
            .description("Skin layer toggle delay, in milliseconds.")
            .defaultValue(10)
            .min(0)
            .max(500)
            .sliderRange(0, 500)
            .visible(() -> !randomizeDelay.get())
            .build());

    private final Setting<Integer> minRandomDelay = sgDelay.add(new IntSetting.Builder()
            .name("min-delay")
            .description("Minimum delay (milliseconds).")
            .defaultValue(5)
            .min(0)
            .max(500)
            .sliderRange(0, 500)
            .visible(() -> randomizeDelay.get())
            .build());

    private final Setting<Integer> maxRandomDelay = sgDelay.add(new IntSetting.Builder()
            .name("max-delay")
            .description("Maximum delay (milliseconds).")
            .defaultValue(50)
            .min(0)
            .max(500)
            .sliderRange(0, 500)
            .visible(() -> randomizeDelay.get())
            .build());

    private final Setting<PartsMode> partsMode = sgMultipleParts.add(new EnumSetting.Builder<PartsMode>()
            .name("parts-mode")
            .description("How many parts to toggle at once.")
            .defaultValue(PartsMode.ONE_BY_ONE)
            .onChanged(v -> resetFlickerState())
            .build());

    private final Setting<Boolean> hatEnabled = sgParts.add(new BoolSetting.Builder()
            .name("hat")
            .description("Include hat in flicker.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> jacketEnabled = sgParts.add(new BoolSetting.Builder()
            .name("jacket")
            .description("Include jacket in flicker.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> leftSleeveEnabled = sgParts.add(new BoolSetting.Builder()
            .name("left-sleeve")
            .description("Include left sleeve in flicker.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> rightSleeveEnabled = sgParts.add(new BoolSetting.Builder()
            .name("right-sleeve")
            .description("Include right sleeve in flicker.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> leftPantsEnabled = sgParts.add(new BoolSetting.Builder()
            .name("left-pants")
            .description("Include left pants leg in flicker.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> rightPantsEnabled = sgParts.add(new BoolSetting.Builder()
            .name("right-pants")
            .description("Include right pants leg in flicker.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> capeEnabled = sgParts.add(new BoolSetting.Builder()
            .name("cape")
            .description("Include cape in flicker.")
            .defaultValue(true)
            .build());

    private static final PlayerModelPart[] HORIZONTAL_PARTS = {
            PlayerModelPart.LEFT_SLEEVE,
            PlayerModelPart.LEFT_PANTS_LEG,
            PlayerModelPart.JACKET,
            PlayerModelPart.HAT,
            PlayerModelPart.CAPE,
            PlayerModelPart.RIGHT_PANTS_LEG,
            PlayerModelPart.RIGHT_SLEEVE
    };

    private static final PlayerModelPart[] VERTICAL_PARTS = {
            PlayerModelPart.HAT,
            PlayerModelPart.JACKET,
            PlayerModelPart.CAPE,
            PlayerModelPart.LEFT_SLEEVE,
            PlayerModelPart.RIGHT_SLEEVE,
            PlayerModelPart.LEFT_PANTS_LEG,
            PlayerModelPart.RIGHT_PANTS_LEG
    };

    private static final PlayerModelPart[] HORIZONTAL_LEFT = { PlayerModelPart.LEFT_SLEEVE };
    private static final PlayerModelPart[] HORIZONTAL_RIGHT = { PlayerModelPart.RIGHT_SLEEVE };
    private static final PlayerModelPart[] HORIZONTAL_CENTER = {
            PlayerModelPart.LEFT_PANTS_LEG,
            PlayerModelPart.RIGHT_PANTS_LEG,
            PlayerModelPart.JACKET,
            PlayerModelPart.HAT,
            PlayerModelPart.CAPE
    };

    private static final PlayerModelPart[] VERTICAL_TOP = { PlayerModelPart.HAT };
    private static final PlayerModelPart[] VERTICAL_BOTTOM = { PlayerModelPart.LEFT_PANTS_LEG,
            PlayerModelPart.RIGHT_PANTS_LEG };
    private static final PlayerModelPart[] VERTICAL_MIDDLE = {
            PlayerModelPart.JACKET,
            PlayerModelPart.LEFT_SLEEVE,
            PlayerModelPart.RIGHT_SLEEVE,
            PlayerModelPart.CAPE
    };

    private long lastToggleTime = 0;
    private int lastIndex = 0;
    private boolean allPartsEnabled = true;

    public SkinFlicker() {
        super(Main.CATEGORY, "skin-flicker", "Toggle your skin layers rapidly for a cool skin effect.");
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null || mc.options == null)
            return;

        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (isPartAvailable(part)) {
                mc.options.setPlayerModelPart(part, true);
            }
        }
    }

    private void resetFlickerState() {
        if (isActive() && mc.options != null) {
            List<PlayerModelPart> availableParts = getAvailableParts();
            for (PlayerModelPart part : availableParts) {
                mc.options.setPlayerModelPart(part, false);
            }
            lastIndex = 0;
        }
    }

    @EventHandler

    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.options == null)
            return;

        long currentTime = System.currentTimeMillis();
        int currentDelay = delay.get();

        if (randomizeDelay.get()) {
            currentDelay = minRandomDelay.get() + (int) (Math.random() * (maxRandomDelay.get() - minRandomDelay.get()));
        }

        if (currentTime - lastToggleTime < currentDelay)
            return;

        lastToggleTime = currentTime;

        if (partsMode.get() == PartsMode.ALL_SIMULTANEOUSLY) {
            toggleAllSkinParts();
        } else {
            List<PlayerModelPart> availableParts = getAvailableParts();

            if (availableParts.isEmpty())
                return;

            switch (mode.get()) {
                case RANDOM -> {
                    if (partsMode.get() == PartsMode.MULTIPLE_PARTS) {
                        int partsToToggle = 1 + (int) (Math.random() * availableParts.size());
                        Collections.shuffle(availableParts);
                        for (int i = 0; i < Math.min(partsToToggle, availableParts.size()); i++) {
                            togglePart(availableParts.get(i));
                        }
                    } else {
                        PlayerModelPart part = availableParts.get((int) (Math.random() * availableParts.size()));
                        togglePart(part);
                    }
                }
                case VERTICAL -> {
                    if (partsMode.get() == PartsMode.MULTIPLE_PARTS) {
                        int maxSteps = 3;
                        if (lastIndex >= maxSteps)
                            lastIndex = 0;

                        int step = (verticalDirection.get() == VerticalDirection.TOP_TO_BOTTOM)
                                ? lastIndex
                                : (maxSteps - 1 - lastIndex);

                        PlayerModelPart[] partsToToggle = switch (step) {
                            case 0 -> VERTICAL_TOP;
                            case 1 -> VERTICAL_MIDDLE;
                            case 2 -> VERTICAL_BOTTOM;
                            default -> new PlayerModelPart[0];
                        };

                        for (PlayerModelPart part : partsToToggle) {
                            if (isPartAvailable(part)) {
                                togglePart(part);
                            }
                        }
                        lastIndex = (lastIndex + 1) % maxSteps;
                    } else {
                        if (lastIndex >= VERTICAL_PARTS.length)
                            lastIndex = 0;

                        int len = VERTICAL_PARTS.length;
                        int index = (verticalDirection.get() == VerticalDirection.TOP_TO_BOTTOM)
                                ? lastIndex
                                : (len - 1 - lastIndex);

                        PlayerModelPart part = VERTICAL_PARTS[index];
                        if (isPartAvailable(part)) {
                            togglePart(part);
                        }
                        lastIndex = (lastIndex + 1) % len;
                    }
                }
                case HORIZONTAL -> {
                    if (partsMode.get() == PartsMode.MULTIPLE_PARTS) {
                        int maxSteps = 3;
                        if (lastIndex >= maxSteps)
                            lastIndex = 0;

                        int step = (horizontalDirection.get() == HorizontalDirection.LEFT_TO_RIGHT)
                                ? lastIndex
                                : (maxSteps - 1 - lastIndex);

                        PlayerModelPart[] partsToToggle = switch (step) {
                            case 0 -> HORIZONTAL_LEFT;
                            case 1 -> HORIZONTAL_CENTER;
                            case 2 -> HORIZONTAL_RIGHT;
                            default -> new PlayerModelPart[0];
                        };

                        for (PlayerModelPart part : partsToToggle) {
                            if (isPartAvailable(part)) {
                                togglePart(part);
                            }
                        }
                        lastIndex = (lastIndex + 1) % maxSteps;
                    } else {
                        if (lastIndex >= HORIZONTAL_PARTS.length)
                            lastIndex = 0;

                        int len = HORIZONTAL_PARTS.length;
                        int index = (horizontalDirection.get() == HorizontalDirection.LEFT_TO_RIGHT)
                                ? lastIndex
                                : (len - 1 - lastIndex);

                        PlayerModelPart part = HORIZONTAL_PARTS[index];
                        if (isPartAvailable(part)) {
                            togglePart(part);
                        }
                        lastIndex = (lastIndex + 1) % len;
                    }
                }
            }
        }

        if (mc.player.networkHandler != null) {
            sendClientSettingsPacket();
        }
    }

    private void togglePart(PlayerModelPart part) {
        boolean isEnabled = mc.options.isPlayerModelPartEnabled(part);
        mc.options.setPlayerModelPart(part, !isEnabled);
    }

    private void toggleAllSkinParts() {
        allPartsEnabled = !allPartsEnabled;
        List<PlayerModelPart> availableParts = getAvailableParts();
        for (PlayerModelPart part : availableParts) {
            mc.options.setPlayerModelPart(part, allPartsEnabled);
        }
    }

    private List<PlayerModelPart> getAvailableParts() {
        List<PlayerModelPart> available = new ArrayList<>();

        if (hatEnabled.get())
            available.add(PlayerModelPart.HAT);
        if (jacketEnabled.get())
            available.add(PlayerModelPart.JACKET);
        if (leftSleeveEnabled.get())
            available.add(PlayerModelPart.LEFT_SLEEVE);
        if (rightSleeveEnabled.get())
            available.add(PlayerModelPart.RIGHT_SLEEVE);
        if (leftPantsEnabled.get())
            available.add(PlayerModelPart.LEFT_PANTS_LEG);
        if (rightPantsEnabled.get())
            available.add(PlayerModelPart.RIGHT_PANTS_LEG);
        if (capeEnabled.get())
            available.add(PlayerModelPart.CAPE);

        return available;
    }

    private boolean isPartAvailable(PlayerModelPart part) {
        return switch (part) {
            case HAT -> hatEnabled.get();
            case JACKET -> jacketEnabled.get();
            case LEFT_SLEEVE -> leftSleeveEnabled.get();
            case RIGHT_SLEEVE -> rightSleeveEnabled.get();
            case LEFT_PANTS_LEG -> leftPantsEnabled.get();
            case RIGHT_PANTS_LEG -> rightPantsEnabled.get();
            case CAPE -> capeEnabled.get();
        };
    }

    private void sendClientSettingsPacket() {
        if (mc.player == null || mc.player.networkHandler == null || mc.options == null)
            return;

        try {

            SyncedClientOptions syncedClientOptions = mc.options.getSyncedOptions();
            ClientOptionsC2SPacket packet = new ClientOptionsC2SPacket(syncedClientOptions);
            mc.player.networkHandler.sendPacket(packet);
        } catch (Exception e) {

        }
    }

}
