package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.Main;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.List;

public class IGN extends Module {
    public IGN() {
        super(Main.CATEGORY, "ign", "Ignore gray names");
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        Text text = event.getMessage();
        if (text == null)
            return;

        List<Text> siblings = text.getSiblings();

        if (siblings.isEmpty())
            return;

        Text firstPart = siblings.get(0);
        List<Text> firstPartSiblings = firstPart.getSiblings();

        boolean hasExtraInFirstPart = !firstPartSiblings.isEmpty();

        if (!hasExtraInFirstPart) {
            TextColor color = firstPart.getStyle().getColor();
            TextColor gray = TextColor.fromFormatting(Formatting.GRAY);

            if (color != null && color.equals(gray)) {
                event.cancel();
            }
        }
    }
}
