package com.sanhiruzu.createlittlenetworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sanhiruzu.createlittlenetworks.panel.MimicPanelBlock;
import com.sanhiruzu.createlittlenetworks.panel.MimicPanelBlockEntity;
import com.sanhiruzu.createlittlenetworks.panel.PanelDocument;
import com.sanhiruzu.createlittlenetworks.panel.PanelOrientation;
import com.sanhiruzu.createlittlenetworks.panel.PanelWidget;
import com.sanhiruzu.createlittlenetworks.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Matrix4f;

public final class MimicPanelRenderer implements BlockEntityRenderer<MimicPanelBlockEntity> {
    public MimicPanelRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MimicPanelBlockEntity tile, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        MimicPanelBlockEntity controller = tile.controller();
        if (controller == null) return;
        PanelDocument document = controller.document();
        Direction facing = tile.getBlockState().getValue(MimicPanelBlock.FACING);
        Direction right = PanelOrientation.right(facing);
        Direction up = PanelOrientation.up(facing);
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f pose = poseStack.last().pose();
        int tileCellX = tile.tileX() * PanelOrientation.CELLS_PER_BLOCK;
        int tileCellY = tile.tileY() * PanelOrientation.CELLS_PER_BLOCK;

        drawFrame(tile, quads, pose, right, up, facing);
        boolean editing = Minecraft.getInstance().player != null
                && (Minecraft.getInstance().player.getMainHandItem().is(ModItems.PANEL_DESIGNER.get())
                || Minecraft.getInstance().player.getOffhandItem().is(ModItems.PANEL_DESIGNER.get()));
        if (editing) drawGrid(quads, pose, right, up, facing);
        for (PanelWidget widget : document.widgets()) {
            if (!widget.intersects(tileCellX, tileCellY, tileCellX + 8, tileCellY + 8)) continue;
            float x0 = Mth.clamp((widget.x() - tileCellX) / 8F, 0, 1);
            float y0 = Mth.clamp((widget.y() - tileCellY) / 8F, 0, 1);
            float x1 = Mth.clamp((widget.maxX() - tileCellX) / 8F, 0, 1);
            float y1 = Mth.clamp((widget.maxY() - tileCellY) / 8F, 0, 1);
            drawWidget(widget, x0, y0, x1, y1, quads, pose, right, up, facing);
            if (widget.type().name().contains("LABEL") || widget.type().name().contains("COUNTER")
                    || widget.type().name().contains("SUMMARY") || widget.type().name().contains("STATION")) {
                String text = widget.label().isBlank() ? defaultText(widget) : widget.label();
                drawText(text, x0 + .03F, y1 - .06F, Math.max(.08F, x1 - x0 - .06F), poseStack,
                        buffers, light, right, up, facing);
            }
        }
        if (editing && PanelClientInteractions.selectedOperation() == com.sanhiruzu.createlittlenetworks.network.PanelEditPayload.Operation.PLACE
                && PanelClientInteractions.selectedWidget() != null
                && Minecraft.getInstance().hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(tile.getBlockPos())) {
            int cellX = PanelOrientation.cellX(tile.getBlockPos(), hit.getLocation(), facing);
            int cellY = PanelOrientation.cellY(tile.getBlockPos(), hit.getLocation(), facing);
            float x0 = cellX / 8F;
            float y0 = cellY / 8F;
            float x1 = Math.min(1, (cellX + PanelClientInteractions.selectedWidget().defaultWidth()) / 8F);
            float y1 = Math.min(1, (cellY + PanelClientInteractions.selectedWidget().defaultHeight()) / 8F);
            quad(quads, pose, right, up, facing, x0, y0, x1, y1, .016F, 0x6676DDE8);
            outline(quads, pose, right, up, facing, x0, y0, x1, y1, .0165F, 0xFF76DDE8);
        }
    }

    private static void drawGrid(VertexConsumer consumer, Matrix4f pose, Direction right, Direction up, Direction normal) {
        int color = 0x3576DDE8;
        for (int i = 1; i < 8; i++) {
            float p = i / 8F;
            quad(consumer, pose, right, up, normal, p - .0015F, .025F, p + .0015F, .975F, .014F, color);
            quad(consumer, pose, right, up, normal, .025F, p - .0015F, .975F, p + .0015F, .014F, color);
        }
    }

    private static void outline(VertexConsumer consumer, Matrix4f pose, Direction right, Direction up, Direction normal,
                                float x0, float y0, float x1, float y1, float depth, int color) {
        quad(consumer, pose, right, up, normal, x0, y0, x1, y0 + .008F, depth, color);
        quad(consumer, pose, right, up, normal, x0, y1 - .008F, x1, y1, depth, color);
        quad(consumer, pose, right, up, normal, x0, y0, x0 + .008F, y1, depth, color);
        quad(consumer, pose, right, up, normal, x1 - .008F, y0, x1, y1, depth, color);
    }

    private static void drawFrame(MimicPanelBlockEntity tile, VertexConsumer consumer, Matrix4f pose,
                                  Direction right, Direction up, Direction normal) {
        int frame = 0xFF292B2B;
        int green = 0xFF314E3B;
        if (tile.tileX() == 0) quad(consumer, pose, right, up, normal, 0, 0, .025F, 1, .002F, frame);
        if (tile.tileY() == 0) quad(consumer, pose, right, up, normal, 0, 0, 1, .025F, .002F, frame);
        if (tile.tileX() == tile.regionWidth() - 1) quad(consumer, pose, right, up, normal, .975F, 0, 1, 1, .002F, frame);
        if (tile.tileY() == tile.regionHeight() - 1) quad(consumer, pose, right, up, normal, 0, .975F, 1, 1, .002F, frame);
        quad(consumer, pose, right, up, normal, .025F, .035F, .975F, .11F, .003F, green);
    }

    private static void drawWidget(PanelWidget widget, float x0, float y0, float x1, float y1,
                                   VertexConsumer consumer, Matrix4f pose, Direction right, Direction up,
                                   Direction normal) {
        int accent = widget.color().getTextureDiffuseColor();
        int active = widget.type().isControl() ? widget.controlValue() : widget.value();
        int lit = active > 0 ? brighten(accent, Math.min(1F, .35F + active / 15F)) : darken(accent, .28F);
        int bezel = 0xFF242728;
        float pad = Math.min(.035F, Math.min(x1 - x0, y1 - y0) * .18F);
        switch (widget.type()) {
            case ZONE -> quad(consumer, pose, right, up, normal, x0, y0, x1, y1, .004F, (accent & 0x00FFFFFF) | 0x65000000);
            case TRACE, BOUND_ROUTE -> {
                float cy = (y0 + y1) / 2;
                float thickness = active > 0 ? .025F : .014F;
                quad(consumer, pose, right, up, normal, x0, cy - thickness, x1, cy + thickness, .006F,
                        widget.type() == com.sanhiruzu.createlittlenetworks.panel.PanelWidgetType.TRACE ? darken(accent, .55F) : lit);
            }
            case ARROW -> {
                float cy = (y0 + y1) / 2;
                quad(consumer, pose, right, up, normal, x0, cy - .018F, x1 - .08F, cy + .018F, .006F, accent);
                line(consumer, pose, right, up, normal, x1 - .12F, cy - .08F, x1, cy, .022F, .006F, accent);
                line(consumer, pose, right, up, normal, x1, cy, x1 - .12F, cy + .08F, .022F, .006F, accent);
            }
            case GAUGE -> {
                quad(consumer, pose, right, up, normal, x0, y0, x1, y1, .006F, bezel);
                quad(consumer, pose, right, up, normal, x0 + pad, y0 + pad, x1 - pad, y1 - pad, .008F, 0xFFE7E0CD);
                float cx = (x0 + x1) / 2;
                float cy = y0 + pad * 1.4F;
                double angle = Math.toRadians(155 - Math.min(15, active) / 15F * 130F);
                float length = Math.min(x1 - x0, y1 - y0) * .37F;
                line(consumer, pose, right, up, normal, cx, cy, cx + (float) Math.cos(angle) * length,
                        cy + (float) Math.sin(angle) * length, .014F, .011F, 0xFF242424);
            }
            case BAR_METER -> {
                quad(consumer, pose, right, up, normal, x0, y0, x1, y1, .006F, bezel);
                float fraction = Math.min(15, active) / 15F;
                quad(consumer, pose, right, up, normal, x0 + pad, y0 + pad, x1 - pad,
                        y0 + pad + (y1 - y0 - pad * 2) * fraction, .009F, lit);
            }
            case LAMP, MOMENTARY_BUTTON, TOGGLE_SWITCH -> {
                quad(consumer, pose, right, up, normal, x0 + pad, y0 + pad, x1 - pad, y1 - pad, .007F, bezel);
                quad(consumer, pose, right, up, normal, x0 + pad * 2, y0 + pad * 2,
                        x1 - pad * 2, y1 - pad * 2, .011F, lit);
            }
            case SELECTOR, ROTARY_DIAL -> {
                quad(consumer, pose, right, up, normal, x0 + pad, y0 + pad, x1 - pad, y1 - pad, .007F, bezel);
                float cx = (x0 + x1) / 2;
                float cy = (y0 + y1) / 2;
                double angle = Math.toRadians(225 - widget.controlValue() / 15F * 270F);
                float length = Math.min(x1 - x0, y1 - y0) * .3F;
                line(consumer, pose, right, up, normal, cx, cy, cx + (float) Math.cos(angle) * length,
                        cy + (float) Math.sin(angle) * length, .025F, .011F, 0xFFD9B35B);
            }
            case STATION -> {
                quad(consumer, pose, right, up, normal, x0 + pad, y0 + pad, x1 - pad, y1 - pad, .007F, bezel);
                quad(consumer, pose, right, up, normal, x0 + pad * 2, y0 + pad * 2, x1 - pad * 2,
                        y1 - pad * 2, .009F, lit);
            }
            case COURIER_STATUS -> {
                quad(consumer, pose, right, up, normal, x0 + pad, y0 + pad, x1 - pad, y1 - pad, .007F, bezel);
                float cy = (y0 + y1) / 2;
                quad(consumer, pose, right, up, normal, x0 + pad * 2, cy - .035F, x1 - pad * 2, cy + .035F, .01F, lit);
            }
            default -> {
                quad(consumer, pose, right, up, normal, x0 + pad, y0 + pad, x1 - pad, y1 - pad, .006F, bezel);
                quad(consumer, pose, right, up, normal, x0 + pad * 1.5F, y0 + pad * 1.5F,
                        x1 - pad * 1.5F, y1 - pad * 1.5F, .008F, 0xFFD8CFB8);
            }
        }
    }

    private static String defaultText(PanelWidget widget) {
        return switch (widget.type()) {
            case COUNTER, DEMAND_COUNTER, NETWORK_SUMMARY -> Integer.toString(widget.value());
            case STATION -> "STN";
            case TEXT_LABEL -> "LABEL";
            case NAMEPLATE -> "PANEL";
            default -> "";
        };
    }

    private static void drawText(String text, float u, float v, float available, PoseStack poseStack,
                                 MultiBufferSource buffers, int light, Direction right, Direction up,
                                 Direction normal) {
        if (text.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        float scale = Math.min(1F / 64F, available / Math.max(1, font.width(text)));
        Vec origin = point(right, up, normal, u, v, .013F);
        Matrix4f local = new Matrix4f().set(
                right.getStepX() * scale, -up.getStepX() * scale, normal.getStepX() * scale, origin.x,
                right.getStepY() * scale, -up.getStepY() * scale, normal.getStepY() * scale, origin.y,
                right.getStepZ() * scale, -up.getStepZ() * scale, normal.getStepZ() * scale, origin.z,
                0, 0, 0, 1);
        Matrix4f matrix = new Matrix4f(poseStack.last().pose()).mul(local);
        font.drawInBatch(text, 0, 0, 0xFF1E2422, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose, Direction right, Direction up, Direction normal,
                             float u0, float v0, float u1, float v1, float depth, int color) {
        Vec a = point(right, up, normal, u0, v0, depth);
        Vec b = point(right, up, normal, u1, v0, depth);
        Vec c = point(right, up, normal, u1, v1, depth);
        Vec d = point(right, up, normal, u0, v1, depth);
        vertex(consumer, pose, a, color);
        vertex(consumer, pose, b, color);
        vertex(consumer, pose, c, color);
        vertex(consumer, pose, d, color);
    }

    private static void line(VertexConsumer consumer, Matrix4f pose, Direction right, Direction up, Direction normal,
                             float x0, float y0, float x1, float y1, float width, float depth, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < .0001F) return;
        float px = -dy / length * width;
        float py = dx / length * width;
        Vec a = point(right, up, normal, x0 + px, y0 + py, depth);
        Vec b = point(right, up, normal, x0 - px, y0 - py, depth);
        Vec c = point(right, up, normal, x1 - px, y1 - py, depth);
        Vec d = point(right, up, normal, x1 + px, y1 + py, depth);
        vertex(consumer, pose, a, color);
        vertex(consumer, pose, b, color);
        vertex(consumer, pose, c, color);
        vertex(consumer, pose, d, color);
    }

    private static Vec point(Direction right, Direction up, Direction normal, float u, float v, float depth) {
        float supportOffset = .375F;
        return new Vec(.5F + right.getStepX() * (u - .5F) + up.getStepX() * (v - .5F)
                - normal.getStepX() * supportOffset + normal.getStepX() * depth,
                .5F + right.getStepY() * (u - .5F) + up.getStepY() * (v - .5F)
                        - normal.getStepY() * supportOffset + normal.getStepY() * depth,
                .5F + right.getStepZ() * (u - .5F) + up.getStepZ() * (v - .5F)
                        - normal.getStepZ() * supportOffset + normal.getStepZ() * depth);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Vec point, int color) {
        consumer.addVertex(pose, point.x, point.y, point.z).setColor(color);
    }

    private static int brighten(int color, float amount) {
        int r = Math.min(255, (int) (((color >> 16) & 255) * amount + 80 * amount));
        int g = Math.min(255, (int) (((color >> 8) & 255) * amount + 80 * amount));
        int b = Math.min(255, (int) ((color & 255) * amount + 80 * amount));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int darken(int color, float amount) {
        int r = (int) (((color >> 16) & 255) * amount);
        int g = (int) (((color >> 8) & 255) * amount);
        int b = (int) ((color & 255) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private record Vec(float x, float y, float z) {}
}
