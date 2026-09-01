package com.hwy.devpivot.agent.config;

import com.hwy.devpivot.env.ModelConfig;

import java.util.List;

/**
 * 单个 agent 定义，不同 type 对应不同字段子集。
 * <ul>
 *   <li>worker: agentClass, systemMessage, tools</li>
 *   <li>loop: subAgents, maxIterations, exitCondition</li>
 *   <li>supervisor: subAgents, contextGenerationStrategy, responseStrategy, description=supervisorContext</li>
 * </ul>
 */
public class AgentDef {

    /** worker / loop / supervisor */
    private String type;
    private String name;
    private String outputKey;
    /** 通用描述，supervisor 类型下作为 supervisorContext */
    private String description;
    /** coding 类型：LangChain4j agent 接口类全限定名 */
    private String agentClass;
    /** coding 类型：systemMessage 资源路径 */
    private String systemMessage;
    /** coding 类型：工具类全限定名列表 */
    private List<String> tools;
    /** loop / supervisor 类型：嵌套的子 agent 定义列表 */
    private List<AgentDef> subAgents;
    /** loop 类型：最大迭代次数 */
    private int maxIterations;
    /** loop 类型：退出条件 */
    private ExitConditionDef exitCondition;
    /** supervisor 类型：上下文策略 */
    private String contextGenerationStrategy;
    /** supervisor 类型：最大 agent 并发调用数 */
    private Integer maxAgentsInvocations;
    /** supervisor 类型：响应策略 */
    private String responseStrategy;
    /** agent 级别 model 配置，为 null 时使用全局配置 */
    private ModelConfig model;

    // ── getters / setters ──────────────────────────────
    public String getType()                    { return type; }
    public void setType(String type)           { this.type = type; }
    public String getName()                    { return name; }
    public void setName(String name)           { this.name = name; }
    public String getOutputKey()               { return outputKey != null ? outputKey : name; }
    public void setOutputKey(String outputKey) { this.outputKey = outputKey; }
    public String getDescription()             { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAgentClass()              { return agentClass; }
    public void setAgentClass(String agentClass) { this.agentClass = agentClass; }
    public String getSystemMessage()           { return systemMessage; }
    public void setSystemMessage(String systemMessage) { this.systemMessage = systemMessage; }
    public List<String> getTools()             { return tools; }
    public void setTools(List<String> tools)   { this.tools = tools; }
    public List<AgentDef> getSubAgents()                { return subAgents; }
    public void setSubAgents(List<AgentDef> subAgents)  { this.subAgents = subAgents; }
    public int getMaxIterations()              { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
    public ExitConditionDef getExitCondition() { return exitCondition; }
    public void setExitCondition(ExitConditionDef exitCondition) { this.exitCondition = exitCondition; }
    public String getContextGenerationStrategy() { return contextGenerationStrategy != null ? contextGenerationStrategy : "CHAT_MEMORY_AND_SUMMARIZATION"; }
    public void setContextGenerationStrategy(String s) { this.contextGenerationStrategy = s; }
    public String getResponseStrategy()        { return responseStrategy != null ? responseStrategy : "SUMMARY"; }
    public void setResponseStrategy(String responseStrategy) { this.responseStrategy = responseStrategy; }
    public Integer getMaxAgentsInvocations()        { return maxAgentsInvocations; }
    public void setMaxAgentsInvocations(Integer maxAgentsInvocations) { this.maxAgentsInvocations = maxAgentsInvocations; }
    public ModelConfig getModel()              { return model; }
    public void setModel(ModelConfig model)    { this.model = model; }

    /**
     * 获取融合后的有效 model 配置：以全局配置为底，agent 级别的非空配置覆盖。
     */
    public ModelConfig getMergedModel(ModelConfig global) {
        if (model == null) return global;
        ModelConfig merged = new ModelConfig();
        merged.setBaseUrl(isNotBlank(model.getBaseUrl()) ? model.getBaseUrl() : global.getBaseUrl());
        merged.setApiKey(isNotBlank(model.getApiKey()) ? model.getApiKey() : global.getApiKey());
        merged.setModelName(isNotBlank(model.getModelName()) ? model.getModelName() : global.getModelName());
        merged.setTemperature(model.getTemperature());
        merged.setLogRequests(model.isLogRequests());
        merged.setLogResponses(model.isLogResponses());
        return merged;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
