package com.hwy.devpivot.agent;

import com.hwy.devpivot.agent.input.TaskExtInfo;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WorkerAgent {

    @Agent
    @UserMessage("""
       任务描述:
          {{taskDesc}}
    """)
    TokenStream run(@MemoryId String memoryId, @V("taskDesc")String taskDesc);

}
