package com.hwy.devpivot.agent.config;

/**
 * agents.json 顶层配置，rootAgent 为嵌套 agent 树的根节点。
 */
public class AgentConfig {

    private AgentDef rootAgent;

    public AgentDef getRootAgent()                { return rootAgent; }
    public void setRootAgent(AgentDef rootAgent)  { this.rootAgent = rootAgent; }
}
