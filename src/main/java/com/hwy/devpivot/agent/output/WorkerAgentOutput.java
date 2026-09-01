package com.hwy.devpivot.agent.output;

import dev.langchain4j.model.output.structured.Description;


public class WorkerAgentOutput {

    @Description("最后一次大模型回答是否需要调用工具（call tools），需要调用值为`Y`，不需要调用值为`N`")
    private String hasToolExecutionRequests;

    @Description("最后一次大模型回答的内容")
    private String aiMessage;

    public String getHasToolExecutionRequests() {
        return hasToolExecutionRequests;
    }

    public void setHasToolExecutionRequests(String hasToolExecutionRequests) {
        this.hasToolExecutionRequests = hasToolExecutionRequests;
    }

    public String getAiMessage() {
        return aiMessage;
    }

    public void setAiMessage(String aiMessage) {
        this.aiMessage = aiMessage;
    }
}
