package com.sanhiruzu.createlittlenetworks;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CourierConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SERVER = new Server(builder);
        SERVER_SPEC = builder.build();
    }

    private CourierConfig() {}

    public static final class Server {
        public final ModConfigSpec.IntValue transferTimeoutTicks;
        public final ModConfigSpec.IntValue transitTimeoutTicks;

        Server(ModConfigSpec.Builder builder) {
            builder.push("courier");
            transferTimeoutTicks = builder.comment("Maximum inactivity at a provider or requester before recovery.")
                    .defineInRange("transferTimeoutTicks", 20 * 120, 20, 20 * 60 * 60);
            transitTimeoutTicks = builder.comment("Maximum unladen job inactivity before returning to the depot.")
                    .defineInRange("transitTimeoutTicks", 20 * 60 * 10, 20 * 30, 20 * 60 * 60);
            builder.pop();
        }
    }
}
