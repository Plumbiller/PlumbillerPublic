package com.Plumbiller.publicaddon.mixin;

import com.Plumbiller.publicaddon.ui.ModInfoScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add PlumbillerPublic info button to title screen
 */
@Mixin(TitleScreen.class)
public abstract class PauseScreenMixin extends Screen {

    public PauseScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addModInfoButton(CallbackInfo ci) {
        // Add PlumbillerPublic button on the left side, aligned with Singleplayer button
        ButtonWidget infoButton = ButtonWidget.builder(Text.literal("PlumbillerPublic"), button ->
            MinecraftClient.getInstance().setScreen(new ModInfoScreen(this))
        )
            .dimensions(10, 10, 100, 20)
            .build();

        // Call the protected method from within the mixin
        this.addDrawableChild(infoButton);
    }
}

