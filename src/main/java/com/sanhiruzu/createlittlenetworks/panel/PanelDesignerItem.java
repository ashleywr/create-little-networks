package com.sanhiruzu.createlittlenetworks.panel;

import com.sanhiruzu.createlittlenetworks.client.PanelClientInteractions;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class PanelDesignerItem extends Item {
    public PanelDesignerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof MimicPanelBlock)) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            if (FMLEnvironment.dist == Dist.CLIENT) PanelClientInteractions.useDesigner(context);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
