package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.Main;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.RenameItemC2SPacket;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;

public class AutoRename extends Module {
    private static final int MAIN_INVENTORY_START = 3;
    private static final int HOTBAR_START = 30;
    private static final int HOTBAR_SIZE = 9;
    private static final int MAX_WAIT_TICKS = 40;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> targets = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items that should be renamed when an anvil is open.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<Boolean> usePrefix = sgGeneral.add(new BoolSetting.Builder()
        .name("use-prefix")
        .description("Adds a prefix before the base name.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> prefixText = sgGeneral.add(new StringSetting.Builder()
        .name("prefix-text")
        .description("Text used as prefix when enabled.")
        .defaultValue("")
        .visible(usePrefix::get)
        .build()
    );

    private final Setting<Boolean> useSuffix = sgGeneral.add(new BoolSetting.Builder()
        .name("use-suffix")
        .description("Adds a suffix after the base name.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> suffixText = sgGeneral.add(new StringSetting.Builder()
        .name("suffix-text")
        .description("Text used as suffix when enabled.")
        .defaultValue("")
        .visible(useSuffix::get)
        .build()
    );

    private final Setting<Boolean> keepOriginal = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-original-name")
        .description("Keeps the default item name between the prefix and suffix.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> replacementText = sgGeneral.add(new StringSetting.Builder()
        .name("replacement-text")
        .description("Base name to use when not keeping the original name.")
        .defaultValue("")
        .visible(() -> !keepOriginal.get())
        .build()
    );

    private Stage stage = Stage.IDLE;
    private int currentSlot = -1;
    private int waitTicks;

    public AutoRename() {
        super(Main.CATEGORY, "auto-rename", "Renames selected items automatically while an anvil is open.");
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    private void onScreenChange(OpenScreenEvent event) {
        if (!(event.screen instanceof AnvilScreen)) resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate() || mc.player == null || mc.interactionManager == null) return;
        if (targets.get().isEmpty()) {
            resetState();
            return;
        }

        if (!(mc.player.currentScreenHandler instanceof AnvilScreenHandler handler)) {
            resetState();
            return;
        }

        switch (stage) {
            case IDLE -> tryStartRename(handler);
            case WAITING_OUTPUT -> waitForOutput(handler);
            case RETURNING -> returnRenamedItem(handler);
        }
    }

    private void tryStartRename(AnvilScreenHandler handler) {
        if (!handler.getCursorStack().isEmpty()) return;
        if (!handler.getSlot(0).getStack().isEmpty() || !handler.getSlot(2).getStack().isEmpty()) return;

        int slot = findNextCandidate();
        if (slot == -1) return;

        ItemStack stack = mc.player.getInventory().getStack(slot);
        String name = buildTargetName(stack);
        if (name.isBlank()) return;
        if (stack.getName().getString().equals(name)) return;

        if (!moveToInput(handler, slot)) {
            resetState();
            return;
        }

        sendRenamePacket(name);
        stage = Stage.WAITING_OUTPUT;
        currentSlot = slot;
        waitTicks = 0;
    }

    private void waitForOutput(AnvilScreenHandler handler) {
        waitTicks++;
        if (handler.getSlot(2).hasStack()) {
            stage = Stage.RETURNING;
            return;
        }

        if (waitTicks > MAX_WAIT_TICKS || !handler.getSlot(0).hasStack()) {
            moveInputBack(handler);
            resetState();
        }
    }

    private void returnRenamedItem(AnvilScreenHandler handler) {
        if (!handler.getSlot(2).hasStack()) {
            moveInputBack(handler);
            resetState();
            return;
        }

        int handlerSlot = toHandlerSlot(currentSlot);
        click(handler, 2);
        click(handler, handlerSlot);
        resetState();
    }

    private void moveInputBack(AnvilScreenHandler handler) {
        if (currentSlot < 0) return;

        int handlerSlot = toHandlerSlot(currentSlot);
        if (handlerSlot == -1) return;
        if (handler.getSlot(0).hasStack()) {
            click(handler, 0);
            click(handler, handlerSlot);
        }
    }

    private boolean moveToInput(AnvilScreenHandler handler, int invSlot) {
        if (mc.player == null) return false;
        int handlerSlot = toHandlerSlot(invSlot);
        if (handlerSlot == -1) return false;

        click(handler, handlerSlot);
        click(handler, 0);
        return handler.getSlot(0).hasStack();
    }

    private void click(AnvilScreenHandler handler, int slotId) {
        mc.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
    }

    private void sendRenamePacket(String name) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.sendPacket(new RenameItemC2SPacket(name));
    }

    private int findNextCandidate() {
        if (mc.player == null) return -1;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (!targets.get().contains(stack.getItem())) continue;
            String name = buildTargetName(stack);
            if (name.isBlank()) continue;
            if (!stack.getName().getString().equals(name)) return i;
        }
        return -1;
    }

    private String buildTargetName(ItemStack stack) {
        String base = keepOriginal.get() ? getDefaultName(stack) : replacementText.get();
        if (base.isBlank()) return "";

        StringBuilder builder = new StringBuilder();
        if (usePrefix.get()) builder.append(prefixText.get());
        builder.append(base);
        if (useSuffix.get()) builder.append(suffixText.get());
        return builder.toString();
    }

    private String getDefaultName(ItemStack stack) {
        return stack.getItem().getName(stack).getString();
    }

    private int toHandlerSlot(int invSlot) {
        if (invSlot < 0 || mc.player == null) return -1;
        if (invSlot >= HOTBAR_SIZE && invSlot < mc.player.getInventory().size()) {
            return MAIN_INVENTORY_START + (invSlot - HOTBAR_SIZE);
        }
        if (invSlot < HOTBAR_SIZE) return HOTBAR_START + invSlot;
        return -1;
    }

    private void resetState() {
        stage = Stage.IDLE;
        currentSlot = -1;
        waitTicks = 0;
    }

    private enum Stage {
        IDLE,
        WAITING_OUTPUT,
        RETURNING
    }
}
