package com.Plumbiller.publicaddon.mixin;

import com.Plumbiller.publicaddon.modules.BetterTabFix;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Mixin(PlayerListHud.class)
public class BetterTabFixMixin {

    @Inject(method = "getPlayerName", at = @At("HEAD"), cancellable = true)
    private void onGetPlayerName(PlayerListEntry entry, CallbackInfoReturnable<Text> info) {
        BetterTabFix module = Modules.get().get(BetterTabFix.class);
        if (module != null && module.isActive()) {
            info.setReturnValue(module.getPlayerName(entry));
        }
    }

    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 0)
    private List<PlayerListEntry> modifyPlayerList(List<PlayerListEntry> visibleEntries) {
        BetterTabFix module = Modules.get().get(BetterTabFix.class);
        if (module != null && module.isActive()) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || mc.player.networkHandler == null)
                return visibleEntries;

            List<PlayerListEntry> allEntries = new java.util.ArrayList<>(mc.player.networkHandler.getPlayerList());

            allEntries.sort(Comparator.comparingInt(
                    (PlayerListEntry entry) -> entry.getGameMode() == net.minecraft.world.GameMode.SPECTATOR ? 1 : 0)
                    .thenComparing(
                            entry -> entry.getScoreboardTeam() != null ? entry.getScoreboardTeam().getName() : "")
                    .thenComparing(entry -> com.Plumbiller.publicaddon.util.MultiVersionCompat
                            .getProfileName(entry.getProfile()), String::compareToIgnoreCase));

            module.totalPlayerCount = allEntries.size();

            int offset = module.scrollableTab.get() ? module.scrollOffset : 0;
            int limit = module.tabSize.get();

            int maxOffset = Math.max(0, allEntries.size() - limit);
            if (offset > maxOffset) {
                offset = maxOffset;
                if (module.scrollableTab.get()) {
                    module.scrollOffset = offset;
                }
            }

            List<PlayerListEntry> list = allEntries.stream()
                    .skip(offset)
                    .limit(limit)
                    .collect(Collectors.toList());

            int rows = module.tabHeight.get();
            if (rows > 0 && !list.isEmpty()) {
                int cols = (list.size() + rows - 1) / rows;
                int targetSize = cols * rows;
                int needed = targetSize - list.size();

                if (needed > 0) {
                    try {
                        PlayerListEntry dummy = new PlayerListEntry(new GameProfile(UUID.randomUUID(), "   "), false);
                        for (int i = 0; i < needed; i++) {
                            list.add(dummy);
                        }
                    } catch (Throwable e) {
                    }
                }
            }
            return list;
        }
        return visibleEntries;
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 20))
    private int modifyTabHeight(int value) {
        BetterTabFix module = Modules.get().get(BetterTabFix.class);
        if (module != null && module.isActive()) {
            return module.tabHeight.get();
        }
        return value;
    }
}
