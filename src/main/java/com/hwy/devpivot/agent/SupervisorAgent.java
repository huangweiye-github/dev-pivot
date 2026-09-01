package com.hwy.devpivot.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;

public interface SupervisorAgent {
    @Agent
    ResultWithAgenticScope<String> invoke(@MemoryId String memoryId, @V("request") String request);
}
