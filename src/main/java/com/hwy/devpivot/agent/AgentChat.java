package com.hwy.devpivot.agent;

import com.alibaba.fastjson.JSON;
import com.hwy.devpivot.agent.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Agent 启动入口，加载配置并委托 {@link AgentFactory} 构建 agent 拓扑。
 */
public class AgentChat {

    private static final Logger logger = LoggerFactory.getLogger(AgentChat.class);
    private static final String CONFIG_PATH = "agents/default-agent.json";

    /** 按 conversationId 缓存 SupervisorAgent，实现多轮对话复用 */
    private static final ConcurrentMap<String, SupervisorAgent> agentCache = new ConcurrentHashMap<>();
    /** 按 conversationId 缓存 AgentFactory，提供 memoryId 查询 */
    private static final ConcurrentMap<String, AgentFactory> factoryCache = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        String start = AgentChat.chat("你是什么模型？你之前完成了什么任务？帮我评审 AgentBoot代码", System.currentTimeMillis() + "");
        System.out.println(start);
    }

    public static String chat(String userMessage, String conversationId) {
        logger.debug("AgentBoot.start conversationId={}, userMessage={}", conversationId, userMessage);
        AgentFactory factory = factoryCache.computeIfAbsent(conversationId, id -> {
            logger.info("创建新的 AgentFactory, conversationId={}, memoryId={}", id, id);
            return new AgentFactory(id);
        });
        SupervisorAgent agentSupervisor = agentCache.computeIfAbsent(conversationId, id -> {
            AgentConfig config = loadConfig();
            logger.info("创建新的 SupervisorAgent, conversationId={}", id);
            return factory.build(config);
        });
        // invoke 之前即可通过 factory.getMemoryId() 获取 memoryId
        Object invoke = agentSupervisor.invoke(conversationId,userMessage).result();
        String result = invoke != null ? invoke.toString() : "";
        logger.info("Supervisor invoke result: {}", result);
        return result;
    }

    private static AgentConfig loadConfig() {
        InputStream is = AgentChat.class.getClassLoader().getResourceAsStream(CONFIG_PATH);
        if (is == null) {
            throw new IllegalStateException("未找到 " + CONFIG_PATH);
        }
        try (is) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return JSON.parseObject(content, AgentConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("读取 " + CONFIG_PATH + " 失败", e);
        }
    }
}
