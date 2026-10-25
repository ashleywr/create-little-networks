package com.sanhiruzu.createlittlenetworks.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sanhiruzu.createlittlenetworks.routing.CourierDispatcher;
import com.sanhiruzu.createlittlenetworks.routing.CourierNetworkData;
import com.sanhiruzu.createlittlenetworks.routing.LittleLogisticsPackagePortTarget;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import dev.murad.shipping.block.dockingstation.DockingStationBlock;
import dev.murad.shipping.block.dockingstation.DockingStationBlockEntity;
import dev.murad.shipping.block.dockingstation.DockingStationRegistry;
import dev.murad.shipping.capability.StallingCapability;
import dev.murad.shipping.entity.custom.DispatchableHeadVehicle;
import dev.murad.shipping.entity.custom.train.locomotive.SteamLocomotiveEntity;
import dev.murad.shipping.entity.custom.vessel.barge.ChestBargeEntity;
import dev.murad.shipping.entity.custom.vessel.tug.SteamTugEntity;
import dev.murad.shipping.global.VehicleRegistrationData;
import dev.murad.shipping.item.LocoRouteItem;
import dev.murad.shipping.setup.ModBlocks;
import dev.murad.shipping.setup.ModEntityTypes;
import dev.murad.shipping.setup.ModItems;
import dev.murad.shipping.transport.TransportEdge;
import dev.murad.shipping.transport.TransportMode;
import dev.murad.shipping.transport.TransportNetwork;
import dev.murad.shipping.transport.TransportNetworkData;
import dev.murad.shipping.transport.TransportNode;
import dev.murad.shipping.util.TugRoutePoint;
import dev.murad.shipping.util.TugRouteSegment;
import dev.murad.shipping.util.LocoRoute;
import dev.murad.shipping.util.LocoRouteCompiler;
import dev.murad.shipping.util.LocoRouteNode;
import dev.murad.shipping.util.LocoRouteSegment;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds and observes a deterministic real-world courier run in a development client. */
public final class LogisticsVerificationCommand {
    private static final String SENDER = "CLN Sender";
    private static final String RECEIVER = "CLN Receiver";
    private static final String VERIFICATION_ENTITY_TAG = "create_little_networks.verification";
    private static final String RELOAD_ENTITY_TAG = "create_little_networks.reload_verification";
    private static final String RELOAD_ARMED_TAG = "CLNReloadArmed";
    private static final String RELOAD_X_TAG = "CLNReloadX";
    private static final String RELOAD_Z_TAG = "CLNReloadZ";
    private static VerificationRun activeRun;
    private static int armedLevelIdentity;

    private LogisticsVerificationCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cln")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("verify")
                        .then(Commands.literal("setup").executes(context -> setup(context.getSource())))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
                        .then(Commands.literal("dock-capabilities")
                                .executes(context -> dockCapabilities(context.getSource())))
                        .then(Commands.literal("multipart-capabilities")
                                .executes(context -> multipartCapabilities(context.getSource())))
                        .then(Commands.literal("loco-reload-setup")
                                .executes(context -> locomotiveReloadSetup(context.getSource())))
                        .then(Commands.literal("loco-reload-arm")
                                .executes(context -> locomotiveReloadArm(context.getSource())))
                        .then(Commands.literal("loco-reload-status")
                                .executes(context -> locomotiveReloadStatus(context.getSource())))));
    }

    private static int setup(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        cleanupPrevious(level);

        int baseX = Math.floorDiv(player.blockPosition().getX(), 16) * 16;
        int baseZ = Math.floorDiv(player.blockPosition().getZ(), 16) * 16;
        int waterY = Math.clamp(player.blockPosition().getY() + 8, 80, 240);
        BlockPos origin = new BlockPos(baseX, waterY, baseZ);
        int senderX = baseX + 2;
        int receiverX = baseX + 26;

        discardTaggedFixtureEntities(level, senderX, receiverX, waterY, baseZ);

        clearAndFillCanal(level, origin, senderX, receiverX);
        List<DockingStationBlockEntity> senderDocks = placeStation(level, senderX, waterY, baseZ, SENDER);
        List<DockingStationBlockEntity> receiverDocks = placeStation(level, receiverX, waterY, baseZ, RECEIVER);
        DockingStationBlockEntity senderDock = senderDocks.get(1);
        DockingStationBlockEntity receiverDock = receiverDocks.get(1);
        UUID senderStation = requireStation(senderDock);
        UUID receiverStation = requireStation(receiverDock);

        BlockPos senderPostboxPos = new BlockPos(senderX, waterY, baseZ + 2);
        BlockPos receiverPostboxPos = new BlockPos(receiverX, waterY, baseZ + 2);
        PostboxBlockEntity senderPostbox = placePostbox(level, senderPostboxPos, senderDock, SENDER);
        PostboxBlockEntity receiverPostbox = placePostbox(level, receiverPostboxPos, receiverDock, RECEIVER);

        ItemStack packageStack = PackageItem.containing(List.of(new ItemStack(Items.DIAMOND, 3)));
        PackageItem.addAddress(packageStack, RECEIVER);
        if (!senderPostbox.inventory.insertItem(0, packageStack, false).isEmpty()) {
            throw new IllegalStateException("Could not insert the verification package into the sender postbox");
        }
        senderPostbox.setChanged();

        TransportNetworkData networkData = TransportNetworkData.get(level.getServer());
        TransportNetwork network = networkData.create("CLN Automated Verification", TransportMode.WATER);
        TransportNode senderNode = new TransportNode(UUID.randomUUID(), SENDER,
                level.dimension().location().toString(), senderDock.getVehicleBlockPos(), senderStation);
        TransportNode receiverNode = new TransportNode(UUID.randomUUID(), RECEIVER,
                level.dimension().location().toString(), receiverDock.getVehicleBlockPos(), receiverStation);
        network.putNode(senderNode);
        network.putNode(receiverNode);
        network.putEdge(TransportEdge.water(senderNode.id(), receiverNode.id(),
                straightSegment(senderX, receiverX, waterY, baseZ)));
        network.putEdge(TransportEdge.water(receiverNode.id(), senderNode.id(),
                straightSegment(receiverX, senderX, waterY, baseZ)));
        networkData.changed();

        // The head vehicle occupies the leading dock in its direction of travel; otherwise
        // LL correctly treats the next controller as a pass-through slot for a longer consist.
        DockingStationBlockEntity senderHeadDock = senderDocks.getLast();
        Vec3 senderCenter = Vec3.atCenterOf(senderHeadDock.getVehicleBlockPos());
        SteamTugEntity tug = new SteamTugEntity(level, senderCenter.x, senderCenter.y, senderCenter.z);
        tug.moveTo(senderCenter.x, senderCenter.y, senderCenter.z, Direction.EAST.toYRot(), 0);
        tug.addTag(VERIFICATION_ENTITY_TAG);
        tug.getFuelItemHandler().setStackInSlot(0, new ItemStack(Items.COAL, 8));
        tug.setOwner(player.getUUID());

        Vec3 bargeCenter = Vec3.atCenterOf(senderDock.getVehicleBlockPos());
        ChestBargeEntity barge = new ChestBargeEntity(ModEntityTypes.CHEST_BARGE.get(), level,
                bargeCenter.x, bargeCenter.y, bargeCenter.z);
        barge.moveTo(bargeCenter.x, bargeCenter.y, bargeCenter.z, Direction.EAST.toYRot(), 0);
        barge.addTag(VERIFICATION_ENTITY_TAG);
        if (!level.addFreshEntity(tug) || !level.addFreshEntity(barge)) {
            tug.discard();
            barge.discard();
            throw new IllegalStateException("Could not spawn the verification consist");
        }
        tug.setDominated(barge);
        barge.setDominant(tug);

        if (!tug.getDispatchNavigation().claim(CourierDispatcher.CONTROLLER_ID, network.id())) {
            throw new IllegalStateException("Could not claim the verification tug for dispatch");
        }
        tug.clearDispatchRoute();
        tug.getDispatchNavigation().setTarget(CourierDispatcher.CONTROLLER_ID, senderStation, true);
        tug.setEngineOn(false);

        CourierNetworkData courierData = CourierNetworkData.get(level.getServer());
        courierData.enroll(tug.getUUID(), level.dimension().location().toString(), network.id(),
                senderStation, senderStation, CourierNetworkData.CourierMode.CREATE_COMPATIBLE,
                "", new CompoundTag(), 5, 5);
        VehicleRegistrationData.get(level.getServer()).register(tug, player.getUUID());

        activeRun = new VerificationRun(level.dimension().location().toString(), network.id(),
                tug.getUUID(), barge.getUUID(), senderStation, receiverStation,
                senderPostboxPos, receiverPostboxPos, senderCenter, level.getGameTime());
        player.teleportTo(level, baseX + 14.5, waterY + 7, baseZ + 9.5,
                java.util.Set.of(), 180, 25);
        source.sendSuccess(() -> Component.literal(
                "CLN verification started: one addressed package, real tug/barge navigation, 24-block route."), false);
        return 1;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        if (activeRun == null) {
            source.sendFailure(Component.literal("No CLN verification run is active. Use /cln verify setup."));
            return 0;
        }
        ServerLevel level = source.getServer().getLevel(source.getLevel().dimension());
        if (level == null || !level.dimension().location().toString().equals(activeRun.dimension)) {
            source.sendFailure(Component.literal("The active CLN verification run is in another dimension."));
            return 0;
        }

        Entity tug = level.getEntity(activeRun.tugId);
        Entity bargeEntity = level.getEntity(activeRun.bargeId);
        PostboxBlockEntity sender = level.getBlockEntity(activeRun.senderPostbox) instanceof PostboxBlockEntity value
                ? value : null;
        PostboxBlockEntity receiver = level.getBlockEntity(activeRun.receiverPostbox) instanceof PostboxBlockEntity value
                ? value : null;
        CourierNetworkData data = CourierNetworkData.get(level.getServer());
        CourierNetworkData.Courier courier = data.courier(activeRun.tugId).orElse(null);

        int atSender = countAddressedPackages(sender, RECEIVER);
        int atReceiver = countAddressedPackages(receiver, RECEIVER);
        int inCargo = bargeEntity instanceof ChestBargeEntity barge
                ? countAddressedPackages(barge.getRawHandler(), RECEIVER) : -1;
        boolean hasJob = data.jobForVehicle(activeRun.tugId).isPresent();
        double moved = tug == null ? -1 : tug.position().subtract(activeRun.startPosition)
                .multiply(1, 0, 1).length();
        boolean atDestination = courier != null && activeRun.receiverStation.equals(courier.currentStationId);
        boolean idle = courier != null
                && courier.status == CourierNetworkData.CourierStatus.WAITING_FOR_PACKAGES;
        TransportNetwork network = TransportNetworkData.get(level.getServer()).get(activeRun.networkId).orElse(null);
        boolean routeReady = network != null && network.closedPlan(List.of(
                activeRun.senderStation, activeRun.receiverStation, activeRun.senderStation)).isPresent();
        boolean claimed = tug instanceof DispatchableHeadVehicle head
                && head.getDispatchNavigation().isClaimedBy(CourierDispatcher.CONTROLLER_ID);
        boolean senderOccupied = stationOccupied(level, activeRun.senderStation, activeRun.tugId);
        boolean receiverOccupied = stationOccupied(level, activeRun.receiverStation, activeRun.tugId);
        String endpointDetails = data.endpoints().stream()
                .filter(endpoint -> endpoint.dimension.equals(activeRun.dimension))
                .map(endpoint -> (endpoint.stationId.equals(activeRun.senderStation) ? "sender" :
                        endpoint.stationId.equals(activeRun.receiverStation) ? "receiver" : "other")
                        + ":'" + endpoint.address + "'=" + endpoint.outbound)
                .collect(Collectors.joining(";"));
        boolean passed = atSender == 0 && atReceiver == 1 && inCargo == 0 && !hasJob
                && atDestination && idle && moved > 20;
        long elapsed = level.getGameTime() - activeRun.startedAt;

        String details = "sender=" + atSender + ", cargo=" + inCargo + ", receiver=" + atReceiver
                + ", job=" + (hasJob ? "active" : "closed")
                + ", station=" + (atDestination ? "receiver" : "sender/transit")
                + ", status=" + (courier == null ? "missing" : courier.status)
                + ", route=" + routeReady + ", claimed=" + claimed
                + ", senderDock=" + senderOccupied + ", receiverDock=" + receiverOccupied
                + ", endpoints=[" + endpointDetails + "]"
                + ", moved=" + String.format(java.util.Locale.ROOT, "%.1f", moved)
                + " blocks, ticks=" + elapsed;
        if (passed) {
            source.sendSuccess(() -> Component.literal("CLN VERIFY PASS — " + details), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("CLN verify running — " + details), false);
        return 0;
    }

    private static int dockCapabilities(CommandSourceStack source) throws CommandSyntaxException {
        if (activeRun == null) {
            source.sendFailure(Component.literal("No CLN verification run is active. Use /cln verify setup."));
            return 0;
        }
        ServerLevel level = source.getPlayerOrException().serverLevel();
        Entity entity = level.getEntity(activeRun.tugId);
        if (!(entity instanceof SteamTugEntity tug)) {
            source.sendFailure(Component.literal("The verification tug is not loaded."));
            return 0;
        }
        List<BlockPos> members = DockingStationRegistry.getOrCreate(level)
                .snapshot(activeRun.senderStation).stream()
                .flatMap(station -> station.members().stream())
                .toList();
        for (BlockPos controllerPos : members) {
            if (!(level.getBlockEntity(controllerPos) instanceof DockingStationBlockEntity dock)
                    || !dock.getVehicleBlockPos().equals(tug.blockPosition())) {
                continue;
            }
            boolean alreadyOccupied = dock.isOccupiedBy(tug);
            if (!dock.tryOccupyDock(tug)) continue;
            try {
                IItemHandler controller = level.getCapability(
                        Capabilities.ItemHandler.BLOCK, controllerPos, Direction.UP);
                IItemHandler upper = level.getCapability(
                        Capabilities.ItemHandler.BLOCK, controllerPos.above(), Direction.UP);
                int controllerSlots = controller == null ? 0 : controller.getSlots();
                boolean passed = controller != null && controllerSlots > 0
                        && upper != null && upper == controller
                        && upper.getSlots() == controllerSlots;
                String details = "controller=" + controllerPos + ", controllerSlots=" + controllerSlots
                        + ", upper=" + controllerPos.above() + ", upperSlots="
                        + (upper == null ? "missing" : upper.getSlots())
                        + ", sameHandler=" + (upper == controller);
                if (passed) {
                    source.sendSuccess(() -> Component.literal("CLN DOCK CAPABILITY PASS — " + details), false);
                    return 1;
                }
                source.sendFailure(Component.literal("CLN DOCK CAPABILITY FAIL — " + details));
                return 0;
            } finally {
                if (!alreadyOccupied) dock.vacateDock(tug);
            }
        }
        source.sendFailure(Component.literal("No sender dock aligned with the verification tug."));
        return 0;
    }

    private static int multipartCapabilities(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        AABB bounds = AABB.ofSize(player.position(), 160, 80, 160);
        List<Entity> heads = level.getEntitiesOfClass(Entity.class, bounds,
                entity -> entity instanceof SteamTugEntity || entity instanceof SteamLocomotiveEntity);
        int checked = 0;
        for (Entity head : heads) {
            for (Entity part : head.getParts()) {
                checked++;
                try {
                    IItemHandler items = part.getCapability(Capabilities.ItemHandler.ENTITY, null);
                    StallingCapability stalling = part.getCapability(StallingCapability.ENTITY_CAPABILITY, null);
                    if (items != null || stalling != null) {
                        source.sendFailure(Component.literal("Multipart capability leak on "
                                + head.getType() + ": items=" + (items != null)
                                + ", stalling=" + (stalling != null)));
                        return 0;
                    }
                } catch (ClassCastException exception) {
                    source.sendFailure(Component.literal("Multipart capability cast failed for "
                            + head.getType() + ": " + exception));
                    return 0;
                }
            }
        }
        if (checked == 0) {
            source.sendFailure(Component.literal("No tug or locomotive multipart hitboxes were loaded."));
            return 0;
        }
        int partCount = checked;
        source.sendSuccess(() -> Component.literal(
                "CLN MULTIPART CAPABILITY PASS — checked=" + partCount + ", exposed=0"), false);
        return 1;
    }

    private static int locomotiveReloadSetup(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        int baseX = Math.floorDiv(player.blockPosition().getX(), 16) * 16;
        int baseZ = Math.floorDiv(player.blockPosition().getZ(), 16) * 16;
        int railY = Math.clamp(player.blockPosition().getY() + 2, 72, 230);
        int maxX = baseX + 20;
        int maxZ = baseZ + 10;
        AABB bounds = new AABB(baseX - 4, railY - 4, baseZ - 4,
                maxX + 4, railY + 6, maxZ + 4);
        level.getEntitiesOfClass(Entity.class, bounds,
                        entity -> entity.getTags().contains(RELOAD_ENTITY_TAG))
                .forEach(Entity::discard);

        buildRailLoop(level, baseX, maxX, baseZ, maxZ, railY);
        List<BlockPos> waypoints = List.of(
                new BlockPos(baseX + 2, railY, baseZ),
                new BlockPos(maxX, railY, baseZ + 2),
                new BlockPos(maxX - 2, railY, maxZ),
                new BlockPos(baseX, railY, maxZ - 2));
        Direction[] directions = {
                Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH
        };
        List<LocoRouteSegment> segments = new ArrayList<>();
        for (int index = 0; index < waypoints.size(); index++) {
            BlockPos from = waypoints.get(index);
            BlockPos to = waypoints.get((index + 1) % waypoints.size());
            Direction incoming = directions[index];
            Direction arrival = directions[(index + 1) % directions.length];
            LocoRouteCompiler.CompileResult result = LocoRouteCompiler.compile(
                    level, from, to, incoming, arrival);
            if (!result.success() || result.segment() == null) {
                throw new IllegalStateException("Could not compile reload verification rail loop: " + result.error());
            }
            segments.add(result.segment());
        }
        LocoRoute route = new LocoRoute("CLN Reload Verification",
                level.dimension().location().toString(),
                waypoints.stream().map(LocoRouteNode::fromBlockPos).toList(),
                segments, LocoRoute.State.COMPLETE, -1);
        ItemStack routeStack = new ItemStack(ModItems.LOCO_ROUTE.get());
        LocoRouteItem.saveRoute(route, routeStack);

        BlockPos start = waypoints.getFirst();
        SteamLocomotiveEntity locomotive = new SteamLocomotiveEntity(level,
                start.getX() + 0.5, start.getY() + 0.0625, start.getZ() + 0.5);
        locomotive.initializeRailTravelDirection(Direction.EAST);
        locomotive.setYRot(Direction.EAST.toYRot());
        locomotive.setOwner(player.getUUID());
        locomotive.addTag(RELOAD_ENTITY_TAG);
        locomotive.getFuelItemHandler().setStackInSlot(0, new ItemStack(Items.COAL, 64));
        locomotive.getRouteItemHandler().setStackInSlot(0, routeStack);
        locomotive.setEngineOn(true);
        if (!level.addFreshEntity(locomotive)) {
            locomotive.discard();
            throw new IllegalStateException("Could not spawn the reload verification locomotive");
        }
        VehicleRegistrationData.get(level.getServer()).register(locomotive, player.getUUID());
        armedLevelIdentity = 0;

        player.teleportTo(level, baseX + 10.5, railY + 7, baseZ + 16.5,
                java.util.Set.of(), 180, 28);
        source.sendSuccess(() -> Component.literal(
                "CLN locomotive reload fixture started. Wait for movement, then use /cln verify loco-reload-arm."), false);
        return 1;
    }

    private static int locomotiveReloadArm(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SteamLocomotiveEntity locomotive = findReloadLocomotive(player.serverLevel(), player);
        if (locomotive == null) {
            source.sendFailure(Component.literal("No CLN reload verification locomotive is nearby."));
            return 0;
        }
        locomotive.getPersistentData().putBoolean(RELOAD_ARMED_TAG, true);
        locomotive.getPersistentData().putDouble(RELOAD_X_TAG, locomotive.getX());
        locomotive.getPersistentData().putDouble(RELOAD_Z_TAG, locomotive.getZ());
        armedLevelIdentity = System.identityHashCode(player.serverLevel());
        source.sendSuccess(() -> Component.literal(
                "CLN locomotive reload check armed. Close and reopen this world without touching the route item."), false);
        return 1;
    }

    private static int locomotiveReloadStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SteamLocomotiveEntity locomotive = findReloadLocomotive(player.serverLevel(), player);
        if (locomotive == null) {
            source.sendFailure(Component.literal("No CLN reload verification locomotive is nearby."));
            return 0;
        }
        CompoundTag persistent = locomotive.getPersistentData();
        boolean armed = persistent.getBoolean(RELOAD_ARMED_TAG);
        double moved = armed ? Math.hypot(locomotive.getX() - persistent.getDouble(RELOAD_X_TAG),
                locomotive.getZ() - persistent.getDouble(RELOAD_Z_TAG)) : 0;
        boolean reloaded = armedLevelIdentity != 0
                && armedLevelIdentity != System.identityHashCode(player.serverLevel());
        boolean routeUsable = LocoRouteItem.getRoute(
                locomotive.getRouteItemHandler().getStackInSlot(0)).isUsable();
        boolean active = locomotive.isManagedServiceActive();
        boolean passed = armed && reloaded && routeUsable && active && moved > 2;
        String details = "reloaded=" + reloaded + ", route=" + routeUsable + ", active=" + active
                + ", movedSinceArm=" + String.format(java.util.Locale.ROOT, "%.1f", moved) + " blocks";
        if (passed) {
            source.sendSuccess(() -> Component.literal("CLN LOCOMOTIVE RELOAD PASS — " + details), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("CLN locomotive reload check — " + details), false);
        return 0;
    }

    private static SteamLocomotiveEntity findReloadLocomotive(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(SteamLocomotiveEntity.class,
                        player.getBoundingBox().inflate(160, 80, 160),
                        entity -> entity.getTags().contains(RELOAD_ENTITY_TAG))
                .stream().findFirst().orElse(null);
    }

    private static void buildRailLoop(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int railY) {
        for (int x = minX - 2; x <= maxX + 2; x++) {
            for (int z = minZ - 2; z <= maxZ + 2; z++) {
                level.setBlock(new BlockPos(x, railY - 1, z), Blocks.GLASS.defaultBlockState(), 3);
                for (int y = railY; y <= railY + 3; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX + 1; x < maxX; x++) {
            placeRail(level, new BlockPos(x, railY, minZ), RailShape.EAST_WEST);
            placeRail(level, new BlockPos(x, railY, maxZ), RailShape.EAST_WEST);
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            placeRail(level, new BlockPos(minX, railY, z), RailShape.NORTH_SOUTH);
            placeRail(level, new BlockPos(maxX, railY, z), RailShape.NORTH_SOUTH);
        }
        placeRail(level, new BlockPos(minX, railY, minZ), RailShape.SOUTH_EAST);
        placeRail(level, new BlockPos(maxX, railY, minZ), RailShape.SOUTH_WEST);
        placeRail(level, new BlockPos(maxX, railY, maxZ), RailShape.NORTH_WEST);
        placeRail(level, new BlockPos(minX, railY, maxZ), RailShape.NORTH_EAST);
    }

    private static void placeRail(ServerLevel level, BlockPos pos, RailShape shape) {
        level.setBlock(pos, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape), 2);
    }

    private static void cleanupPrevious(ServerLevel level) {
        if (activeRun == null || !level.dimension().location().toString().equals(activeRun.dimension)) return;
        CourierNetworkData.get(level.getServer()).removeCourier(activeRun.tugId);
        TransportNetworkData.get(level.getServer()).remove(activeRun.networkId);
        Entity tug = level.getEntity(activeRun.tugId);
        Entity barge = level.getEntity(activeRun.bargeId);
        if (tug != null) tug.discard();
        if (barge != null) barge.discard();
        activeRun = null;
    }

    private static void discardTaggedFixtureEntities(ServerLevel level, int senderX, int receiverX,
                                                       int waterY, int routeZ) {
        AABB fixtureBounds = new AABB(senderX - 6, waterY - 4, routeZ - 6,
                receiverX + 6, waterY + 8, routeZ + 6);
        level.getEntitiesOfClass(Entity.class, fixtureBounds,
                        entity -> entity.getTags().contains(VERIFICATION_ENTITY_TAG)
                                || entity instanceof SteamTugEntity
                                || entity instanceof ChestBargeEntity)
                .forEach(Entity::discard);
    }

    private static void clearAndFillCanal(ServerLevel level, BlockPos origin, int senderX, int receiverX) {
        int minX = senderX - 4;
        int maxX = receiverX + 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = origin.getZ() - 3; z <= origin.getZ() + 4; z++) {
                for (int y = origin.getY(); y <= origin.getY() + 4; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(new BlockPos(x, origin.getY() - 1, z), Blocks.GLASS.defaultBlockState(), 3);
            }
            for (int z = origin.getZ() - 1; z <= origin.getZ() + 1; z++) {
                level.setBlock(new BlockPos(x, origin.getY(), z), Blocks.WATER.defaultBlockState(), 3);
            }
            level.setBlock(new BlockPos(x, origin.getY(), origin.getZ() - 2), Blocks.GLASS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, origin.getY(), origin.getZ() + 3), Blocks.GLASS.defaultBlockState(), 3);
        }
    }

    private static List<DockingStationBlockEntity> placeStation(ServerLevel level, int centerX, int y,
                                                                  int routeZ, String name) {
        List<DockingStationBlockEntity> docks = new ArrayList<>();
        for (int offset = -1; offset <= 1; offset++) {
            BlockPos pos = new BlockPos(centerX + offset, y, routeZ + 1);
            level.setBlock(pos, ModBlocks.DOCKING_STATION.get().defaultBlockState()
                    .setValue(DockingStationBlock.FACING, Direction.SOUTH), 3);
            if (!(level.getBlockEntity(pos) instanceof DockingStationBlockEntity dock)) {
                throw new IllegalStateException("Docking station block entity was not created at " + pos);
            }
            docks.add(dock);
        }
        for (DockingStationBlockEntity dock : docks) {
            DockingStationBlockEntity.serverTick(level, dock.getBlockPos(), dock.getBlockState(), dock);
        }
        docks.get(1).setStationName(name);
        return docks;
    }

    private static PostboxBlockEntity placePostbox(ServerLevel level, BlockPos pos,
                                                    DockingStationBlockEntity dock, String address) {
        level.setBlock(pos, AllBlocks.PACKAGE_POSTBOXES.get(DyeColor.WHITE).get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof PostboxBlockEntity postbox)) {
            throw new IllegalStateException("Postbox block entity was not created at " + pos);
        }
        postbox.addressFilter = address;
        postbox.acceptsPackages = true;
        LittleLogisticsPackagePortTarget target = new LittleLogisticsPackagePortTarget(
                dock.getBlockPos().subtract(pos));
        postbox.target = target;
        postbox.setChanged();
        target.register(postbox, level, pos);
        return postbox;
    }

    private static UUID requireStation(DockingStationBlockEntity dock) {
        if (dock.getStationId() == null) throw new IllegalStateException("Dock has no station ID");
        return dock.getStationId();
    }

    private static TugRouteSegment straightSegment(int fromX, int toX, int y, int z) {
        List<TugRoutePoint> points = new ArrayList<>();
        int step = Integer.compare(toX, fromX);
        for (int x = fromX; ; x += step) {
            points.add(new TugRoutePoint(x, y, z));
            if (x == toX) break;
        }
        return new TugRouteSegment(points);
    }

    private static int countAddressedPackages(PostboxBlockEntity postbox, String address) {
        return postbox == null ? -1 : countAddressedPackages(postbox.inventory, address);
    }

    private static int countAddressedPackages(net.neoforged.neoforge.items.IItemHandler handler, String address) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (PackageItem.isPackage(stack) && address.equals(PackageItem.getAddress(stack))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean stationOccupied(ServerLevel level, UUID stationId, UUID vehicleId) {
        return DockingStationRegistry.getOrCreate(level).snapshot(stationId).stream()
                .flatMap(station -> station.members().stream())
                .map(level::getBlockEntity)
                .filter(DockingStationBlockEntity.class::isInstance)
                .map(DockingStationBlockEntity.class::cast)
                .anyMatch(dock -> dock.getDockedHeadVehicle() instanceof Entity head
                        && head.getUUID().equals(vehicleId));
    }

    private record VerificationRun(String dimension, UUID networkId, UUID tugId, UUID bargeId,
                                   UUID senderStation, UUID receiverStation,
                                   BlockPos senderPostbox, BlockPos receiverPostbox,
                                   Vec3 startPosition, long startedAt) {}
}
