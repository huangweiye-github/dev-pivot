package com.hwy.devpivot.agent.listener;

import com.alibaba.fastjson.JSON;
import com.hwy.devpivot.cli.CliConsoleManager;
import dev.langchain4j.agentic.observability.*;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvokerAgentListener implements AgentListener {

    private Logger logger = LoggerFactory.getLogger(InvokerAgentListener.class);

    @Override
    public void beforeAgentInvocation(AgentRequest agentRequest) {
        logger.info("beforeAgentInvocation={},memoryId={}",
                agentRequest.agent().name(),
                agentRequest.agenticScope().memoryId());
    }

    @Override
    public void afterAgentInvocation(AgentResponse agentResponse) {
        logger.info("afterAgentInvocation={},memoryId={}",
                agentResponse.agentName(),
                agentResponse.agenticScope().memoryId());
        if(agentResponse.output() instanceof TokenStream tokenStream){
            new ConsoleStreamHandler(CliConsoleManager.get((String) agentResponse.agenticScope().memoryId())).attachTo(agentResponse.agentName(),tokenStream);
        }
    }

    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution beforeAgentToolExecution) {
        BeforeToolExecution beforeToolExecution = beforeAgentToolExecution.toolExecution();
        logger.info("beforeAgentToolExecution={},memoryId={},request={}",
                beforeAgentToolExecution.agentInstance().name(),
                beforeAgentToolExecution.agenticScope().memoryId(),
                beforeToolExecution.request()
        );
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
        ToolExecution toolExecution = afterAgentToolExecution.toolExecution();
        logger.info("afterAgentToolExecution={},memoryId={},request={},result={}",
                afterAgentToolExecution.agentInstance().name(),
                afterAgentToolExecution.agenticScope().memoryId(),
                toolExecution.request(),
                toolExecution.result()
        );
    }


    @Override
    public void onAgentInvocationError( AgentInvocationError agentInvocationError) {
        logger.error("onAgentInvocationError: {},state={} ", agentInvocationError.agentName(),
                JSON.toJSONString(agentInvocationError.agenticScope().state()),
                agentInvocationError.error());
    }
}
