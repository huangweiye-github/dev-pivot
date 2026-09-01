package com.hwy.devpivot.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.hwy.devpivot.agent.config.AgentConfig;
import com.hwy.devpivot.agent.config.AgentDef;
import com.hwy.devpivot.agent.listener.InvokerAgentListener;
import com.hwy.devpivot.agent.listener.LLMChatModelListener;
import com.hwy.devpivot.agent.output.WorkerAgentOutput;
import com.hwy.devpivot.agent.tools.DevPivotTool;
import com.hwy.devpivot.agent.tools.ToolRegistry;
import com.hwy.devpivot.constant.Constant;
import com.hwy.devpivot.context.PersistentChatMemoryStore;
import com.hwy.devpivot.env.EnvironmentReader;
import com.hwy.devpivot.env.ModelConfig;
import com.hwy.devpivot.env.SettingsConfig;
import com.hwy.devpivot.env.SettingsReader;
import com.hwy.devpivot.mcp.McpConfigLoader;
import com.hwy.devpivot.mcp.McpServerConfig;
import com.hwy.devpivot.mcp.McpTransportFactory;
import com.hwy.devpivot.prompt.PromptUtil;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.internal.StreamingResponse;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.skills.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 从 agents.json 配置动态构建 agent 及依赖关系。
 */
public class AgentFactory {

    private static final Logger logger = LoggerFactory.getLogger(AgentFactory.class);

    private static final String TYPE_WORKER = "worker";
    private static final String TYPE_LOOP       = "loop";
    private static final String TYPE_SUPERVISOR = "supervisor";

    private final SettingsConfig settingsConfig;
    private final ChatMemoryProvider workerAgentchatMemoryProvider;
    private final ChatMemoryProvider supervisorAgentchatMemoryProvider;
    private final String conversationId;
    private final LLMChatModelListener llmChatModelListener;
    private final InvokerAgentListener invokerAgentListener;
    private static final PersistentChatMemoryStore store = new PersistentChatMemoryStore();
    public AgentFactory(String conversationId) {
        this.conversationId = conversationId;
        this.settingsConfig = SettingsReader.getSettings();
        this.llmChatModelListener = new LLMChatModelListener();
        this.invokerAgentListener = new InvokerAgentListener();
        workerAgentchatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(store)
                .build();
        supervisorAgentchatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(200)
                .chatMemoryStore(store)
                .build();
    }

    /**
     * 加载配置并递归构建 agent 树，返回根 supervisor。
     */
    public SupervisorAgent build(AgentConfig config) {

        AgentDef root = config.getRootAgent();
        if (root == null) {
            throw new IllegalStateException("agents.json 中缺少 rootAgent 配置");
        }
        if (!TYPE_SUPERVISOR.equals(root.getType())) {
            throw new IllegalStateException("rootAgent 必须是 supervisor 类型，实际: " + root.getType());
        }
        return (SupervisorAgent) buildAgent(root);
    }

    /** 深度优先递归构建 agent 及其子 agent */
    private Object buildAgent(AgentDef def) {
        Object[] subs = buildSubAgents(def.getSubAgents());
        Object agent = switch (def.getType()) {
            case TYPE_WORKER -> buildWorkAgent(def);
            case TYPE_LOOP       -> buildLoopAgent(def, subs);
            case TYPE_SUPERVISOR -> buildSupervisorAgent(def, subs);
            default -> throw new IllegalArgumentException("未知 agent 类型: " + def.getType());
        };
        logger.info("Agent [{}] 构建完成, type={}", def.getName(), def.getType());
        return agent;
    }

    private Object[] buildSubAgents(List<AgentDef> subDefs) {
        if (subDefs == null || subDefs.isEmpty()) return new Object[0];
        Object[] subs = new Object[subDefs.size()];
        for (int i = 0; i < subDefs.size(); i++) {
            subs[i] = buildAgent(subDefs.get(i));
        }
        return subs;
    }

    // ── work agent ────────────────────────────────────
    private WorkerAgent buildWorkAgent(AgentDef def) {
        Object[] tools = instantiateTools(def.getTools());
        String systemPrompt = PromptUtil.readWorkSystemPrompt(def.getSystemMessage(),EnvironmentReader.getAllVariables());
        String desc = PromptUtil.readAgentPrompt(def.getDescription());
        ModelConfig cfg = def.getMergedModel(settingsConfig.getModel());

        // ── MCP ToolProvider：通过 mcp-servers.json 配置化加载 ──
        ToolProvider mcpToolProvider = null;
        Map<String, McpServerConfig> mcpConfigs = McpConfigLoader.load();
        if (!mcpConfigs.isEmpty()) {
            List<McpClient> mcpClients = McpTransportFactory.buildClients(mcpConfigs);
            if (!mcpClients.isEmpty()) {
                mcpToolProvider = McpToolProvider.builder()
                        .mcpClients(mcpClients)
                        .build();
            }
        }
        OpenAiStreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModelName())
                .temperature(cfg.getTemperature())
                .logRequests(cfg.isLogRequests())
                .logResponses(cfg.isLogResponses())
                .listeners(llmChatModelListener)
                .sendThinking(true)
                .returnThinking(true)
                .build();
        ToolProvider skillsToolProvider = null;
        String skillsPrompt = null;
        try {
            List<FileSystemSkill> skills = FileSystemSkillLoader.loadSkills(new File(
                    AgentChat.class.getClassLoader().getResource("skills").toURI()).toPath());
            skillsToolProvider = Skills.from(skills).toolProvider();
            skillsPrompt = Skills.from(skills).formatAvailableSkills();
        } catch (Exception e) {
            logger.error("Load Skill Error! ", e);
        }
        AgentBuilder<WorkerAgent, ?> agentBuilder = AgenticServices.agentBuilder(WorkerAgent.class)
                .name(def.getName())
                .outputKey(def.getOutputKey())
                .description(desc)
                .systemMessage(systemPrompt + "\r\n 你有以下skills：\r\n" + skillsPrompt)
                .streamingChatModel(chatModel)
                .chatMemoryProvider(workerAgentchatMemoryProvider)
                .listener(invokerAgentListener)
                .tools(tools);
                if(mcpToolProvider != null){
                    agentBuilder.toolProviders(mcpToolProvider);
                }
                if(skillsToolProvider != null){
                    agentBuilder.toolProviders(skillsToolProvider);
                }
        return agentBuilder.build();
    }

    // ── loop agent ─────────────────────────────────────
    private UntypedAgent buildLoopAgent(AgentDef def, Object[] subs) {
        String desc = PromptUtil.readAgentPrompt(def.getDescription());
        return AgenticServices.loopBuilder()
                .name(def.getName())
                .beforeCall(agenticScope -> {
                    List<AgentDef> subAgents = def.getSubAgents();
                    for (AgentDef subAgent : subAgents) {
                        agenticScope.state().remove(subAgent.getOutputKey());
                    }
                    agenticScope.state().remove(def.getOutputKey());
                })
                .output(agenticScope -> {
                    List<AgentDef> subAgents = def.getSubAgents();
                    StringBuilder  builder = new StringBuilder();
                    for (AgentDef subAgent : subAgents) {
                        Object output = agenticScope.state().get(subAgent.getOutputKey());
                        if(output instanceof StreamingResponse streamingResponse){
                            builder.append(streamingResponse.toString());
                        }else{
                            builder.append(JSON.toJSONString(output));
                        }
                        builder.append(System.lineSeparator());
                    }
                    return builder.toString();//返回数据，防止planner没收到回复再次规划相同的任务
                })
                .outputKey(def.getOutputKey())
                .description(desc)
                .subAgents(subs)
                .maxIterations(def.getMaxIterations())
                .listener(invokerAgentListener)
                .exitCondition((scope, iterations) -> {
                    logger.debug("exitCondition={}, iterations={}", scope.agentInvocations(), iterations);
                    return def.getSubAgents().stream()
                            .map(item->scope.state().get(item.getOutputKey()))
                            .filter(Objects::nonNull)
                            .map(item->{
                                WorkerAgentOutput workerAgentOutput = null;
                                try {
                                    if (item instanceof StreamingResponse streamOutput) {
                                        String blockingGet = streamOutput.blockingGet();
                                        workerAgentOutput = JSON.parseObject(blockingGet, WorkerAgentOutput.class);
                                    } else {
                                        workerAgentOutput = JSON.parseObject((String) item, WorkerAgentOutput.class);
                                    }
                                } catch (JSONException jsonException) {
                                    return new AbstractMap.SimpleEntry<>(false, workerAgentOutput);
                                } catch (Exception e) {
                                    logger.error("exitCondition error={}", JSON.toJSONString(item), e);
                                    return new AbstractMap.SimpleEntry<>(false, workerAgentOutput);
                                }
                                return new AbstractMap.SimpleEntry<>(Constant.FLAG_Y.equals(workerAgentOutput.getHasToolExecutionRequests()),workerAgentOutput);
                            }).allMatch(item->item.getKey() == false);
                })
                .build();
    }

    // ── supervisor agent ───────────────────────────────
    private SupervisorAgent buildSupervisorAgent(AgentDef def, Object[] subs) {
        String desc = PromptUtil.readAgentPrompt(def.getDescription());

        SupervisorContextStrategy ctxStrategy = SupervisorContextStrategy.valueOf(
                def.getContextGenerationStrategy());
        SupervisorResponseStrategy rspStrategy = SupervisorResponseStrategy.valueOf(
                def.getResponseStrategy());
        ModelConfig cfg = def.getMergedModel(settingsConfig.getModel());
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModelName())
                .temperature(cfg.getTemperature())
                .logRequests(cfg.isLogRequests())
                .logResponses(cfg.isLogResponses())
                .listeners(llmChatModelListener)
                .sendThinking(true)
                .returnThinking(true)
                .build();
        return AgenticServices.supervisorBuilder(SupervisorAgent.class)
                .name(def.getName())
                .outputKey(def.getOutputKey())
                .maxAgentsInvocations(def.getMaxAgentsInvocations())
                .chatModel(chatModel)
                .subAgents(subs)
                .chatMemoryProvider(supervisorAgentchatMemoryProvider)
                .listener(invokerAgentListener)
                .contextGenerationStrategy(ctxStrategy)
                .responseStrategy(rspStrategy)
                .output(agenticScope -> {
                    return null;//返回空字符串，防止二次总结，与subAgent结果输出重复
                })
                .supervisorContext(desc)
                .build();
    }

    private Object[] instantiateTools(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) return new Object[0];
        Map<String, Class<? extends DevPivotTool>> registry = ToolRegistry.getAll();
        Object[] tools = new Object[toolNames.size()];
        for (int i = 0; i < toolNames.size(); i++) {
            String name = toolNames.get(i);
            Class<? extends DevPivotTool> clz = registry.get(name);
            if (clz == null) throw new IllegalArgumentException("未知的 Tool name: " + name);
            try {
                tools[i] = clz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("无法实例化工具: " + name, e);
            }
        }
        return tools;
    }
}
