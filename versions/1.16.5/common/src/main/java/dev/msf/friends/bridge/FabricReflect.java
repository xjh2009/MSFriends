package dev.msf.friends.bridge;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FabricReflect {
    private static final MappingResolver RESOLVER;
    static {
        MappingResolver r = null;
        try { r = FabricLoader.getInstance().getMappingResolver(); }
        catch (Throwable ignored) {}
        RESOLVER = r;
    }

    private static final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
    private static final Map<String, Method> methodCache = new ConcurrentHashMap<>();
    private static final Map<String, Field> fieldCache = new ConcurrentHashMap<>();

    private FabricReflect() {}

    public static Class<?> mcClass(String namedName) throws ClassNotFoundException {
        Class<?> cached = classCache.get(namedName);
        if (cached != null) return cached;
        String runtimeName = resolveClass(namedName);
        Class<?> clazz = Class.forName(runtimeName);
        classCache.put(namedName, clazz);
        return clazz;
    }

    private static String resolveClass(String named) {
        if (RESOLVER != null) {
            try { return RESOLVER.mapClassName("intermediary", named); }
            catch (Throwable ignored) {}
        }
        return named;
    }

    public static Method mcMethod(Class<?> clazz, String namedMethod, Class<?>... paramTypes)
            throws NoSuchMethodException {
        String key = clazz.getName() + "." + namedMethod;
        Method cached = methodCache.get(key);
        if (cached != null) return cached;
        try {
            Method m = clazz.getMethod(namedMethod, paramTypes);
            methodCache.put(key, m);
            return m;
        } catch (NoSuchMethodException ignored) {}
        if (RESOLVER != null) {
            try {
                String namedClass = findNamedClass(clazz);
                if (namedClass != null) {
                    String runtimeName = RESOLVER.mapMethodName("intermediary", namedClass, namedMethod, null);
                    if (runtimeName != null && !runtimeName.equals(namedMethod)) {
                        Method m = clazz.getMethod(runtimeName, paramTypes);
                        methodCache.put(key, m);
                        return m;
                    }
                }
            } catch (Throwable ignored) {}
        }
        throw new NoSuchMethodException("Cannot find method " + namedMethod + " in " + clazz.getName());
    }

    public static Field mcField(Class<?> clazz, String namedField)
            throws NoSuchFieldException {
        String key = clazz.getName() + "." + namedField;
        Field cached = fieldCache.get(key);
        if (cached != null) return cached;
        try {
            Field f = clazz.getDeclaredField(namedField);
            fieldCache.put(key, f);
            return f;
        } catch (NoSuchFieldException ignored) {}
        if (RESOLVER != null) {
            try {
                String namedClass = findNamedClass(clazz);
                if (namedClass != null) {
                    String runtimeName = RESOLVER.mapFieldName("intermediary", namedClass, namedField, null);
                    if (runtimeName != null && !runtimeName.equals(namedField)) {
                        Field f = clazz.getDeclaredField(runtimeName);
                        fieldCache.put(key, f);
                        return f;
                    }
                }
            } catch (Throwable ignored) {}
        }
        throw new NoSuchFieldException("Cannot find field " + namedField + " in " + clazz.getName());
    }

    private static String findNamedClass(Class<?> clazz) {
        for (Map.Entry<String, Class<?>> e : classCache.entrySet()) {
            if (e.getValue().equals(clazz)) return e.getKey();
        }
        return clazz.getName();
    }
}