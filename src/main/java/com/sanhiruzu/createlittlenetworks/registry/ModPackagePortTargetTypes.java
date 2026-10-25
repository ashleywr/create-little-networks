package com.sanhiruzu.createlittlenetworks.registry;

import com.sanhiruzu.createlittlenetworks.CreateLittleNetworks;
import com.sanhiruzu.createlittlenetworks.routing.LittleLogisticsPackagePortTarget;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.logistics.packagePort.PackagePortTargetType;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModPackagePortTargetTypes {
    private static final DeferredRegister<PackagePortTargetType> TARGET_TYPES =
            DeferredRegister.create(CreateRegistries.PACKAGE_PORT_TARGET_TYPE, CreateLittleNetworks.MOD_ID);

    public static final Holder<PackagePortTargetType> LITTLE_LOGISTICS_STATION =
            TARGET_TYPES.register("little_logistics_station", LittleLogisticsPackagePortTarget.Type::new);

    private ModPackagePortTargetTypes() {
    }

    public static void register(IEventBus modBus) {
        TARGET_TYPES.register(modBus);
    }
}

