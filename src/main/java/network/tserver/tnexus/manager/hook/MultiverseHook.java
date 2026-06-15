package network.tserver.tnexus.manager.hook;

import network.tserver.tnexus.manager.MultiverseWorldService;
import org.bukkit.plugin.Plugin;
import org.mvplugins.multiverse.core.MultiverseCore;

/**
 * Required hook for Multiverse-Core.
 */
public final class MultiverseHook implements PluginHook<MultiverseWorldService> {

    private MultiverseWorldService worldService;

    @Override
    public String getPluginName() {
        return "Multiverse-Core";
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public MultiverseWorldService getApi() {
        return this.worldService;
    }

    @Override
    public boolean hook(Plugin plugin) {
        if (plugin == null || !getPluginName().equals(plugin.getName())) {
            return false;
        }

        Object worldManager = resolveWorldManager(plugin);
        if (worldManager == null) {
            return false;
        }

        this.worldService = new MultiverseWorldServiceAdapter(worldManager);
        return true;
    }

    private Object resolveWorldManager(Plugin plugin) {
        if (plugin instanceof MultiverseCore core) {
            try {
                Object api = core.getClass().getMethod("getApi").invoke(core);
                return extractWorldManager(api);
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }

        try {
            Object api = plugin.getClass().getMethod("getApi").invoke(plugin);
            return extractWorldManager(api);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private Object extractWorldManager(Object api) throws ReflectiveOperationException {
        if (api == null) {
            return null;
        }
        return api.getClass().getMethod("getWorldManager").invoke(api);
    }

    private static final class MultiverseWorldServiceAdapter implements MultiverseWorldService {

        private static final String LOAD_WORLD_OPTIONS = "org.mvplugins.multiverse.core.world.options.LoadWorldOptions";
        private static final String IMPORT_WORLD_OPTIONS = "org.mvplugins.multiverse.core.world.options.ImportWorldOptions";
        private static final String LOADED_WORLD_TYPE = "org.mvplugins.multiverse.core.world.LoadedMultiverseWorld";
        private static final String MULTIVERSE_WORLD_TYPE = "org.mvplugins.multiverse.core.world.MultiverseWorld";
        private static final String REGEN_WORLD_OPTIONS = "org.mvplugins.multiverse.core.world.options.RegenWorldOptions";
        private static final String UNLOAD_WORLD_OPTIONS = "org.mvplugins.multiverse.core.world.options.UnloadWorldOptions";

        private final Object worldManager;

        private MultiverseWorldServiceAdapter(Object worldManager) {
            this.worldManager = worldManager;
        }

        @Override
        public boolean unloadWorld(String worldName) {
            Object world = getOrNull(invokeMethod(this.worldManager, "getLoadedWorld", worldName));
            if (world == null) {
                return true;
            }

            Object options = invokeStaticMethod(UNLOAD_WORLD_OPTIONS, "world", LOADED_WORLD_TYPE, world);
            options = invokeMethod(options, "unloadBukkitWorld", true);
            options = invokeMethod(options, "saveBukkitWorld", true);
            return isSuccess(invokeMethod(this.worldManager, "unloadWorld", options));
        }

        @Override
        public boolean removeWorld(String worldName) {
            Object world = getOrNull(invokeMethod(this.worldManager, "getWorld", worldName));
            if (world == null) {
                return true;
            }
            return isSuccess(invokeMethod(this.worldManager, "removeWorld", world));
        }

        @Override
        public boolean importWorld(String worldName, org.bukkit.World.Environment environment) {
            Object options = invokeStaticMethod(IMPORT_WORLD_OPTIONS, "worldName", String.class, worldName);
            options = invokeMethod(options, "environment", environment);
            options = invokeMethod(options, "doFolderCheck", false);
            return isSuccess(invokeMethod(this.worldManager, "importWorld", options));
        }

        @Override
        public boolean regenerateWorld(String worldName, String seed) {
            Object world = getOrNull(invokeMethod(this.worldManager, "getLoadedWorld", worldName));
            if (world == null) {
                return false;
            }

            Object options = invokeStaticMethod(REGEN_WORLD_OPTIONS, "world", LOADED_WORLD_TYPE, world);
            options = invokeMethod(options, "keepGameRule", true);
            options = invokeMethod(options, "keepWorldBorder", true);
            options = invokeMethod(options, "keepWorldConfig", true);
            options = invokeMethod(options, "seed", seed);
            return isSuccess(invokeMethod(this.worldManager, "regenWorld", options));
        }

        @Override
        public boolean loadWorld(String worldName) {
            Object world = getOrNull(invokeMethod(this.worldManager, "getWorld", worldName));
            if (world == null) {
                return false;
            }

            Object options = invokeStaticMethod(LOAD_WORLD_OPTIONS, "world", MULTIVERSE_WORLD_TYPE, world);
            return isSuccess(invokeMethod(this.worldManager, "loadWorld", options));
        }

        private static Object getOrNull(Object option) {
            return option == null ? null : invokeMethod(option, "getOrNull");
        }

        private static boolean isSuccess(Object attempt) {
            return attempt != null && Boolean.TRUE.equals(invokeMethod(attempt, "isSuccess"));
        }

        private static Object invokeStaticMethod(
                String ownerClassName,
                String methodName,
                String parameterTypeName,
                Object argument) {
            try {
                return invokeStaticMethod(ownerClassName, methodName, Class.forName(parameterTypeName), argument);
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException(
                        "Failed to resolve Multiverse parameter type " + parameterTypeName,
                        exception);
            }
        }

        private static Object invokeStaticMethod(
                String ownerClassName,
                String methodName,
                Class<?> parameterType,
                Object argument) {
            try {
                Class<?> ownerClass = Class.forName(ownerClassName);
                if (!parameterType.isInstance(argument)) {
                    return new FluentOptionsShim();
                }
                return ownerClass.getMethod(methodName, parameterType).invoke(null, argument);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to invoke Multiverse static method " + ownerClassName + "#" + methodName, exception);
            }
        }

        private static Object invokeMethod(Object target, String methodName, Object... arguments) {
            try {
                return invokeCompatibleMethod(target, methodName, arguments);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to invoke Multiverse method " + methodName, exception);
            }
        }

        private static Object invokeCompatibleMethod(Object target, String methodName, Object... arguments)
                throws ReflectiveOperationException {
            Class<?> targetType = target.getClass();
            for (java.lang.reflect.Method method : targetType.getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != arguments.length) {
                    continue;
                }
                if (!areParametersCompatible(method.getParameterTypes(), arguments)) {
                    continue;
                }
                return method.invoke(target, arguments);
            }
            throw new NoSuchMethodException(targetType.getName() + "#" + methodName);
        }

        private static boolean areParametersCompatible(Class<?>[] parameterTypes, Object[] arguments) {
            for (int index = 0; index < parameterTypes.length; index++) {
                if (!isParameterCompatible(parameterTypes[index], arguments[index])) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isParameterCompatible(Class<?> parameterType, Object argument) {
            if (argument == null) {
                return !parameterType.isPrimitive();
            }
            if (parameterType.isPrimitive()) {
                return (parameterType == boolean.class && argument instanceof Boolean)
                        || (parameterType == int.class && argument instanceof Integer)
                        || (parameterType == long.class && argument instanceof Long)
                        || (parameterType == double.class && argument instanceof Double);
            }
            return parameterType.isInstance(argument);
        }

        private static final class FluentOptionsShim {

            public FluentOptionsShim unloadBukkitWorld(boolean ignored) {
                return this;
            }

            public FluentOptionsShim saveBukkitWorld(boolean ignored) {
                return this;
            }

            public FluentOptionsShim keepGameRule(boolean ignored) {
                return this;
            }

            public FluentOptionsShim keepWorldBorder(boolean ignored) {
                return this;
            }

            public FluentOptionsShim keepWorldConfig(boolean ignored) {
                return this;
            }

            public FluentOptionsShim seed(String ignored) {
                return this;
            }

            public FluentOptionsShim environment(org.bukkit.World.Environment ignored) {
                return this;
            }

            public FluentOptionsShim doFolderCheck(boolean ignored) {
                return this;
            }
        }
    }
}
