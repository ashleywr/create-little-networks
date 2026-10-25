package com.sanhiruzu.createlittlenetworks.routing;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sanhiruzu.createlittlenetworks.registry.ModPackagePortTargetTypes;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.PackagePortTargetType;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import dev.murad.shipping.block.dockingstation.DockingStationBlockEntity;
import dev.murad.shipping.setup.ModBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** A persistent Create package-port target pointing at a Little Logistics dock controller. */
public final class LittleLogisticsPackagePortTarget extends PackagePortTarget {
    public static final MapCodec<LittleLogisticsPackagePortTarget> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(BlockPos.CODEC.fieldOf("relative_pos").forGetter(target -> target.relativePos))
                    .apply(instance, LittleLogisticsPackagePortTarget::new));

    public static final StreamCodec<ByteBuf, LittleLogisticsPackagePortTarget> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(LittleLogisticsPackagePortTarget::new, target -> target.relativePos);

    public LittleLogisticsPackagePortTarget(BlockPos relativePos) {
        super(relativePos);
    }

    public BlockPos targetPosition(BlockPos portPos) {
        return portPos.offset(relativePos);
    }

    @Override
    public boolean export(LevelAccessor level, BlockPos portPos, ItemStack box, boolean simulate) {
        return false;
    }

    @Override
    public void register(PackagePortBlockEntity port, LevelAccessor level, BlockPos portPos) {
        if (level instanceof ServerLevel serverLevel
                && port instanceof PostboxBlockEntity postbox
                && be(level, portPos) instanceof DockingStationBlockEntity dock
                && dock.getStationId() != null) {
            CourierNetworkData.get(serverLevel.getServer()).registerEndpoint(
                    serverLevel.dimension().location().toString(), dock.getBlockPos(), portPos,
                    dock.getStationId(), CourierNetworkData.EndpointKind.POSTBOX,
                    postbox.getFilterString(), serverLevel.getGameTime());
        }
    }

    @Override
    public void deregister(PackagePortBlockEntity port, LevelAccessor level, BlockPos portPos) {
        if (level instanceof ServerLevel serverLevel) {
            CourierNetworkData.get(serverLevel.getServer()).removeEndpoint(
                    serverLevel.dimension().location().toString(), portPos,
                    CourierNetworkData.EndpointKind.POSTBOX);
        }
    }

    @Override
    public Vec3 getExactTargetLocation(PackagePortBlockEntity port, LevelAccessor level, BlockPos portPos) {
        BlockPos targetPos = portPos.offset(relativePos);
        return be(level, portPos) instanceof DockingStationBlockEntity
                ? Vec3.atCenterOf(targetPos)
                : Vec3.ZERO;
    }

    @Override
    public ItemStack getIcon() {
        return ModBlocks.DOCKING_STATION.get().asItem().getDefaultInstance();
    }

    @Override
    public boolean canSupport(BlockEntity blockEntity) {
        return blockEntity instanceof PostboxBlockEntity;
    }

    @Override
    public boolean depositImmediately() {
        return true;
    }

    @Override
    protected PackagePortTargetType getType() {
        return ModPackagePortTargetTypes.LITTLE_LOGISTICS_STATION.value();
    }

    public static final class Type implements PackagePortTargetType {
        @Override
        public MapCodec<LittleLogisticsPackagePortTarget> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<ByteBuf, LittleLogisticsPackagePortTarget> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
