package com.hwy.devpivot.agent.listener;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class LLMChatModelListener implements ChatModelListener {

    private final Logger logger = LoggerFactory.getLogger(LLMChatModelListener.class);

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        List<ChatMessage> systemMessages = requestContext.chatRequest()
                .messages()
                .stream()
                .filter(item -> ChatMessageType.SYSTEM == item.type())
                .collect(Collectors.toList());
        logger.info("system messages={}",systemMessages);
        logger.debug("===========================onRequest log start=====================================");
        logger.info("onRequest=>{}",requestContext.chatRequest().messages());
        logger.debug("============================onRequest log end====================================");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        logger.debug("===========================onResponse log start=====================================");
        logger.info("onResponse=>{}",responseContext.chatResponse().aiMessage());
        logger.debug("============================onResponse log end====================================");
    }
}
