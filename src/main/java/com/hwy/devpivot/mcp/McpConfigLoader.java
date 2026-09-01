package com.hwy.devpivot.mcp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 加载 mcp-servers.json 配置。
 */
public class McpConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(McpConfigLoader.class);
    private static final String FILE_NAME = "mcps/mcp-servers.json";

    private static volatile Map<String, McpServerConfig> cached;

    private McpConfigLoader() {}

    /**
     * 加载并缓存全部 MCP 服务端配置。
     */
    public static Map<String, McpServerConfig> load() {
        if (cached != null) return cached;
        synchronized (McpConfigLoader.class) {
            if (cached != null) return cached;
            cached = doLoad();
            return cached;
        }
    }

    private static Map<String, McpServerConfig> doLoad() {
        InputStream is = McpConfigLoader.class.getClassLoader().getResourceAsStream(FILE_NAME);
        if (is == null) {
            logger.warn("未找到 {}，MCP 功能不可用", FILE_NAME);
            return Map.of();
        }
        try (is) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(content);
            JSONObject servers = root.getJSONObject("mcpServers");
            if (servers == null || servers.isEmpty()) {
                logger.warn("{} 中 mcpServers 为空", FILE_NAME);
                return Map.of();
            }
            Map<String, McpServerConfig> result = new LinkedHashMap<>();
            for (String name : servers.keySet()) {
                McpServerConfig cfg = servers.getObject(name, McpServerConfig.class);
                result.put(name, cfg);
                logger.info("加载 MCP server: {} (type={})", name, cfg.getType());
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("读取 " + FILE_NAME + " 失败", e);
        }
    }
}
