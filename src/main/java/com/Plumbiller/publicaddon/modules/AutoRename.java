package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.Main;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
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
    private static final int ANVIL_INPUT_SLOT = 0;
    private static final int ANVIL_OUTPUT_SLOT = 2;
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

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The delay in ticks between actions.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private Stage stage = Stage.IDLE;
    private int currentSlot = -1;
    private int waitTicks = 0;
    private int delayTicks = 0;

    public AutoRename() {
        super(Main.CATEGORY, "auto-rename", "Automatically renames items in an anvil.");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    public void onScreenChange(OpenScreenEvent event) {
        if (!(event.screen instanceof AnvilScreen)) {
            resetState();
        }
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate() || mc.player == null || mc.interactionManager == null || !(mc.player.currentScreenHandler instanceof AnvilScreenHandler handler)) {
            resetState();
            return;
        }

        if (targets.get().isEmpty()) {
            resetState();
            return;
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        switch (stage) {
            case IDLE -> tryStartRename(handler);
            case WAITING_FOR_ITEM_IN_SLOT_0 -> waitForItemInSlot0(handler);
            case WAITING_OUTPUT -> waitForOutput(handler);
            case RETURNING -> returnRenamedItem(handler);
        }
    }

    private void tryStartRename(AnvilScreenHandler handler) {
        if (!handler.getCursorStack().isEmpty() || handler.getSlot(ANVIL_INPUT_SLOT).hasStack() || handler.getSlot(ANVIL_OUTPUT_SLOT).hasStack()) {
            return;
        }

        int slot = findItemToRename();
        if (slot == -1) {
            return;
        }

        if (moveToInput(handler, slot)) {
            stage = Stage.WAITING_FOR_ITEM_IN_SLOT_0;
            currentSlot = slot;
            waitTicks = 0;
        } else {
            resetState();
        }
    }

    private void waitForItemInSlot0(AnvilScreenHandler handler) {
        waitTicks++;
        if (handler.getSlot(ANVIL_INPUT_SLOT).hasStack()) {
            ItemStack stack = handler.getSlot(ANVIL_INPUT_SLOT).getStack();
            String name = buildTargetName(stack);
            String currentName = stack.getName().getString();

            if (currentName.equals(name) || alreadyHasCorrectName(currentName)) {
                moveInputBack(handler);
                resetState();
                return;
            }
            sendRenamePacket(name);
            stage = Stage.WAITING_OUTPUT;
            waitTicks = 0;
        } else if (waitTicks > MAX_WAIT_TICKS) {
            resetState();
        }
    }

    private void waitForOutput(AnvilScreenHandler handler) {
        waitTicks++;
        if (handler.getSlot(ANVIL_OUTPUT_SLOT).hasStack()) {
            stage = Stage.RETURNING;
            delayTicks = delay.get();
        } else if (waitTicks > MAX_WAIT_TICKS || !handler.getSlot(ANVIL_INPUT_SLOT).hasStack()) {
            moveInputBack(handler);
            resetState();
        }
    }

    private void returnRenamedItem(AnvilScreenHandler handler) {
        click(handler, ANVIL_OUTPUT_SLOT);
        int handlerSlot = toHandlerSlot(currentSlot);
        if (handlerSlot != -1) {
            click(handler, handlerSlot);
        }
        delayTicks = delay.get();
        resetState();
    }

    private boolean moveToInput(AnvilScreenHandler handler, int invSlot) {
        if (mc.player == null) return false;
        int handlerSlot = toHandlerSlot(invSlot);
        if (handlerSlot == -1) return false;

        click(handler, handlerSlot);
        click(handler, ANVIL_INPUT_SLOT);
        delayTicks = delay.get();
        return true;
    }

    private void moveInputBack(AnvilScreenHandler handler) {
        if (currentSlot == -1) return;
        int handlerSlot = toHandlerSlot(currentSlot);
        if (handlerSlot == -1) return;

        if (handler.getSlot(ANVIL_INPUT_SLOT).hasStack()) {
            click(handler, ANVIL_INPUT_SLOT);
            click(handler, handlerSlot);
        }
        delayTicks = delay.get();
    }

    private void click(AnvilScreenHandler handler, int slotId) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
    }

    private void sendRenamePacket(String name) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.sendPacket(new RenameItemC2SPacket(name));
    }

    private int findItemToRename() {
        if (mc.player == null) return -1;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !targets.get().contains(stack.getItem())) continue;

            String name = buildTargetName(stack);
            String currentName = stack.getName().getString();
            if (currentName.equals(name) || alreadyHasCorrectName(currentName)) continue;

            return i;
        }
        return -1;
    }

    private boolean alreadyHasCorrectName(String currentName) {
        boolean hasPrefix = !usePrefix.get() || currentName.startsWith(prefixText.get());
        boolean hasSuffix = !useSuffix.get() || currentName.endsWith(suffixText.get());
        return hasPrefix && hasSuffix;
    }

    private String buildTargetName(ItemStack stack) {
        String base = keepOriginal.get() ? stack.getName().getString() : replacementText.get();

        StringBuilder builder = new StringBuilder();
        if (usePrefix.get()) builder.append(prefixText.get());
        builder.append(base);
        if (useSuffix.get()) builder.append(suffixText.get());

        String finalName = builder.toString();
        return finalName.length() > 50 ? finalName.substring(0, 50) : finalName;
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
        delayTicks = 0;
    }

    private enum Stage {
        IDLE,
        WAITING_FOR_ITEM_IN_SLOT_0,
        WAITING_OUTPUT,
        RETURNING
    }
}

