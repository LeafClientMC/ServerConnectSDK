package com.leafclient.connect.sdk.paper;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Folia's entity scheduler, called through reflection so the module still compiles against the Bukkit 1.8.8 API.
 * The task of a player follows them across regions and worlds, and Folia retires it when the player leaves.
 */
final class FoliaScheduler implements PlayerScheduler {

    /** A class only Folia has. */
    static final String FOLIA_CLASS = "io.papermc.paper.threadedregions.RegionizedServer";

    private final Method getScheduler;
    private final Method runAtFixedRate;
    private final Method cancel;

    /**
     * Looks up the scheduler methods.
     *
     * @param entityType the entity interface that has {@code getScheduler()}
     * @throws ReflectiveOperationException when a method is missing
     */
    FoliaScheduler(Class<?> entityType) throws ReflectiveOperationException {
        getScheduler = entityType.getMethod("getScheduler");
        runAtFixedRate = getScheduler.getReturnType().getMethod("runAtFixedRate", Plugin.class, Consumer.class, Runnable.class,
                long.class, long.class);
        cancel = runAtFixedRate.getReturnType().getMethod("cancel");
    }

    /**
     * Tells whether the server is Folia.
     *
     * @param loader the class loader to look in
     * @return true when the Folia class is there
     */
    static boolean isFolia(ClassLoader loader) {
        try {
            Class.forName(FOLIA_CLASS, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * Returns the scheduler for the running Folia server.
     *
     * @return the scheduler
     * @throws IllegalStateException when the server lacks the entity scheduler API
     */
    static FoliaScheduler create() {
        try {
            return new FoliaScheduler(Entity.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Folia without the entity scheduler API", e);
        }
    }

    @Override
    public Runnable everyTick(Plugin plugin, Player player, final Runnable task, Runnable retired) {
        Consumer<Object> body = new Consumer<Object>() {
            @Override
            public void accept(Object scheduledTask) {
                task.run();
            }
        };
        final Object handle = call(runAtFixedRate, call(getScheduler, player), plugin, body, retired, 1L, 1L);
        if (handle == null) return null;
        return new Runnable() {
            @Override
            public void run() {
                call(cancel, handle);
            }
        };
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
