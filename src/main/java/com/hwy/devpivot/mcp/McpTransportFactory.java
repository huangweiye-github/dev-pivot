package com.hwy.devpivot.mcp;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 根据 {@link McpServerConfig} 构建 {@link McpTransport} 和 {@link McpClient}。
 */
public class McpTransportFactory {

    private static final Logger logger = LoggerFactory.getLogger(McpTransportFactory.class);

    private McpTransportFactory() {}

    /**
     * 根据配置列表构建全部 McpClient 列表。
     */
    public static List<McpClient> buildClients(Map<String, McpServerConfig> serverConfigs) {
        List<McpClient> clients = new ArrayList<>();
        if (serverConfigs == null || serverConfigs.isEmpty()) return clients;
        for (Map.Entry<String, McpServerConfig> entry : serverConfigs.entrySet()) {
            String name = entry.getKey();
            McpServerConfig cfg = entry.getValue();
            logger.info("构建 MCP client: name={}, type={}", name, cfg.getType());
            try {
                McpTransport transport = buildTransport(cfg);
                McpClient client = new DefaultMcpClient.Builder()
                        .transport(transport)
                        .autoHealthCheck(true)
                        .cachePromptList(true)
                        .cacheToolList(true)
                        .cacheResourceList(true)
                        .build();
                clients.add(client);
            } catch (Exception e) {
                logger.error("McpClient creation error: cfg={}", name, cfg);
            }
        }
        return clients;
    }

    /**
     * 根据单个配置构建 McpTransport。
     */
    public static McpTransport buildTransport(McpServerConfig cfg) {
        if (cfg.isHttp()) {
            return buildHttp(cfg);
        }
        if (cfg.isSSE()) {
            return buildSSE(cfg);
        }
        if (cfg.isStdio()) {
            return buildStdio(cfg);
        }
        throw new IllegalArgumentException("不支持的 MCP transport 类型: " + cfg.getType()
                + "，支持: stdio, streamableHttp");
    }

    private static McpTransport buildSSE(McpServerConfig cfg) {
        if (cfg.getUrl() == null || cfg.getUrl().isBlank()) {
            throw new IllegalArgumentException("sse 类型 MCP 必须配置 url");
        }
        return new HttpMcpTransport.Builder()
                .sseUrl(cfg.getUrl())
                .customHeaders(cfg.getHeaders())
                .timeout(Duration.ofSeconds(cfg.getTimeout()))
                .logRequests(cfg.isLogRequests())
                .logResponses(cfg.isLogResponses())
                .customHeaders(cfg.getHeaders())
                .build();

    }

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private static McpTransport buildStdio(McpServerConfig cfg) {
        if (cfg.getCommand() == null || cfg.getCommand().isBlank()) {
            throw new IllegalArgumentException("stdio 类型 MCP 必须配置 command");
        }
        String cmd = cfg.getCommand();
        // Windows 上 npx/npm/node 等实际可执行文件是 npx.cmd
        if (IS_WINDOWS && !cmd.contains(".")) {
            cmd = cmd + ".cmd";
        }
        List<String> command = new ArrayList<>();
        command.add(cmd);
        if (cfg.getArgs() != null) {
            command.addAll(cfg.getArgs());
        }
        StdioMcpTransport.Builder builder = new StdioMcpTransport.Builder()
                .command(command)
                .logEvents(cfg.isLogEvents());
        if (cfg.getEnv() != null && !cfg.getEnv().isEmpty()) {
            builder.environment(cfg.getEnv());
        }
        return builder.build();
    }

    private static McpTransport buildHttp(McpServerConfig cfg) {
        if (cfg.getUrl() == null || cfg.getUrl().isBlank()) {
            throw new IllegalArgumentException("http 类型 MCP 必须配置 url");
        }
        return new StreamableHttpMcpTransport.Builder()
                .url(cfg.getUrl())
                .customHeaders(cfg.getHeaders())
                .timeout(Duration.ofSeconds(cfg.getTimeout()))
                .logRequests(cfg.isLogRequests())
                .logResponses(cfg.isLogResponses())
                .customHeaders(cfg.getHeaders())
                .build();
    }
}
