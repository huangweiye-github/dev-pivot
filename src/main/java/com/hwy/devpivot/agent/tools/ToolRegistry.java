package com.hwy.devpivot.agent.tools;

import dev.langchain4j.agent.tool.Tool;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Tool 注册中心，通过反射扫描 tools 包自动发现所有 DevPivotTool 实现。
 * 兼容 IDE 目录和 JAR 包两种运行时环境。
 */
public class ToolRegistry {

    private static final String TOOLS_PACKAGE = "com.hwy.devpivot.agent.tools";

    private static final Map<String, Class<? extends DevPivotTool>> registry = new LinkedHashMap<>();

    static {
        try {
            String path = TOOLS_PACKAGE.replace('.', '/');
            Enumeration<URL> resources = ToolRegistry.class.getClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if ("jar".equals(resource.getProtocol())) {
                    scanJar(resource, path);
                } else {
                    scanDir(new File(resource.getFile()), path);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Tool 扫描失败", e);
        }
    }

    private static void scanDir(File dir, String path) {
        if (!dir.isDirectory()) return;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".class"));
        if (files == null) return;
        for (File file : files) {
            String className = TOOLS_PACKAGE + "." + file.getName().replace(".class", "");
            registerIfTool(className);
        }
    }

    private static void scanJar(URL resource, String path) throws IOException {
        String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(path) && name.endsWith(".class") && !name.contains("$")) {
                    String className = name.replace('/', '.').replace(".class", "");
                    registerIfTool(className);
                }
            }
        }
    }

    private static void registerIfTool(String className) {
        try {
            Class<?> clz = Class.forName(className);
            if (DevPivotTool.class.isAssignableFrom(clz)
                    && !Modifier.isAbstract(clz.getModifiers())
                    && !clz.equals(DevPivotTool.class)) {
                @SuppressWarnings("unchecked")
                Class<? extends DevPivotTool> toolClass = (Class<? extends DevPivotTool>) clz;
                registry.put(resolveToolName(toolClass), toolClass);
            }
        } catch (ClassNotFoundException ignored) {}
    }

    public static Map<String, Class<? extends DevPivotTool>> getAll() {
        return new LinkedHashMap<>(registry);
    }

    private static String resolveToolName(Class<? extends DevPivotTool> clz) {
        for (Method m : clz.getDeclaredMethods()) {
            Tool tool = m.getAnnotation(Tool.class);
            if (tool != null && !tool.name().isBlank()) {
                return tool.name();
            }
        }
        throw new IllegalArgumentException(clz.getName() + " 未找到 @Tool(name) 注解");
    }
}
