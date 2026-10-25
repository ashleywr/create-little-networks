package com.sanhiruzu.createlittlenetworks.client;

// Adapted from KeyBind Bundles' RadialMenuRenderer (Copyright 2024 Matyrobbrt, MIT).
// Original notes inspiration from Mekanism's GuiRadialSelector. See the bundled
// META-INF/licenses/KeyBindBundles.txt for the source copyright and license.

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class PanelRadialMenuRenderer {
    public interface Entry {
        Component title();
        ItemStack icon();
    }

    private float[] hoverGrows = new float[0];
    private long lastUpdate;

    public void clearState() {
        hoverGrows = new float[0];
        lastUpdate = 0;
    }

    public void render(GuiGraphics graphics, List<? extends Entry> entries, RadialMenuLayout layout,
                       int selected, int hovered) {
        int count = entries.size();
        if (count == 0) return;
        if (hoverGrows.length != count) hoverGrows = new float[count];
        long now = System.nanoTime();
        float delta = lastUpdate == 0 ? 0 : Math.min(.1F, (now - lastUpdate) / 1_000_000_000F);
        lastUpdate = now;
        for (int i = 0; i < count; i++) {
            hoverGrows[i] += ((i == hovered ? 1 : 0) - hoverGrows[i]) * Math.min(1, delta * 14);
        }

        // Complete GuiGraphics batches before drawing the source renderer's triangle strips.
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2F, graphics.guiHeight() / 2F, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < count; i++) {
            float start = -90F + 360F * (i - .5F) / count;
            float outer = layout.outerRadius() + 5 * layout.scale() * hoverGrows[i];
            int color = i == hovered ? 0xE84A777B : i == selected ? 0xDD6D5B38 : 0xD523292A;
            drawTorus(graphics, start + .65F, 360F / count - 1.3F, layout.innerRadius(), outer, color);
            if (i == hovered || i == selected) {
                drawTorus(graphics, start + .65F, 360F / count - 1.3F, outer - 2 * layout.scale(), outer,
                        i == hovered ? 0xFF76DDE8 : 0xFFD9B35B);
            }
        }
        RenderSystem.disableBlend();

        var font = Minecraft.getInstance().font;
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            double angle = Math.toRadians(-90 + 360D * i / count);
            float x = (float) Math.cos(angle) * layout.labelRadius();
            float y = (float) Math.sin(angle) * layout.labelRadius();
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(layout.scale(), layout.scale(), 1);
            graphics.renderItem(entry.icon(), -8, -17);
            graphics.pose().scale(.65F, .65F, 1);
            List<FormattedCharSequence> lines = font.split(entry.title(), 84);
            for (int line = 0; line < lines.size(); line++) {
                FormattedCharSequence text = lines.get(line);
                graphics.drawString(font, text, -font.width(text) / 2, 4 + line * font.lineHeight,
                        i == hovered ? 0xFFFFFFFF : 0xFFF3E8CF, true);
            }
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }

    private static void drawTorus(GuiGraphics graphics, float startAngle, float sizeAngle,
                                  float inner, float outer, int color) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        var matrix = graphics.pose().last().pose();
        int steps = Math.max(1, (int) Math.ceil(300 * sizeAngle / 360));
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(startAngle + sizeAngle * i / steps);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            buffer.addVertex(matrix, outer * cos, outer * sin, 0).setColor(color);
            buffer.addVertex(matrix, inner * cos, inner * sin, 0).setColor(color);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
