package com.sanhiruzu.createlittlenetworks.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Keeps client-only screen classes out of the dedicated-server classloading path. */
public final class NetworkInspectorClientHandler {
    private static final String SCREEN = "com.sanhiruzu.createlittlenetworks.client.NetworkInspectorScreen";

    private NetworkInspectorClientHandler() {}

    public static void handle(NetworkInspectorPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        try {
            Class<?> screen = Class.forName(SCREEN);
            Method open = screen.getMethod("open", NetworkInspectorPayload.class);
            open.invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Could not open the Create: Little Networks inspector", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Network inspector failed while opening", exception.getCause());
        }
    }
}
