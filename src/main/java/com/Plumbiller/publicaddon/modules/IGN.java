package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.Main;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Optional;

public class IGN extends Module {
    public IGN() {
        super(Main.CATEGORY, "ign", "Ignore gray names");
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        Text text = event.getMessage();
        if (text == null)
            return;

        Optional<Boolean> shouldCancel = text.visit((style, asString) -> {
            if (asString.contains("»")) {
                TextColor color = style.getColor();
                TextColor gray = TextColor.fromFormatting(Formatting.GRAY);

                if (color != null && color.equals(gray)) {
                    return Optional.of(true);
                } else {
                    return Optional.of(false);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);

        if (shouldCancel.isPresent() && shouldCancel.get()) {
            event.cancel();
        }
    }
}
