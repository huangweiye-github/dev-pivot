package com.hwy.devpivot.agent.listener;

import com.hwy.devpivot.cli.ConsoleSession;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.service.TokenStream;
import org.apache.commons.lang3.StringUtils;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 将 {@link TokenStream} 的 partial thinking / response 事件通过 {@link ConsoleSession} 流式输出。
 */
public class ConsoleStreamHandler {

    private final ConsoleSession session;
    private final AttributedStyle headerStyle = new AttributedStyle().foreground(AttributedStyle.CYAN).bold();
    private final AttributedStyle contentStyle = new AttributedStyle().foreground(192, 192, 192).italic();
    boolean thinkingBegin;
    boolean responseBegin;

    public ConsoleStreamHandler(ConsoleSession session) {
        this.session = session;
    }

    /** 将 thinking + response 的流式处理器绑定到 tokenStream 上 */
    public void attachTo(String agentName,TokenStream tokenStream) {
        tokenStream.onPartialThinkingWithContext(onPartialThinkingWithContext(agentName))
                .onPartialResponseWithContext(onPartialResponseWithContext(agentName));
    }


    private BiConsumer<PartialThinking, PartialThinkingContext> onPartialThinkingWithContext(String agentName) {
        return (partialThinking, context) -> {
            String text = partialThinking.text();
            if (session != null && StringUtils.isNotBlank(text)) {
                if (!thinkingBegin) {
                    session.println();
                    session.println();
                    session.println(new AttributedString(agentName+"->Thinking: ", headerStyle).toAnsi());
                    responseBegin = false;
                    thinkingBegin = true;
                }
                session.print(new AttributedString(text, contentStyle).toAnsi());
            }
        };
    }


    private BiConsumer<PartialResponse, PartialResponseContext> onPartialResponseWithContext(String agentName) {
        return (partialResponse, context) -> {
            String text = partialResponse.text();
            if (session != null && StringUtils.isNotBlank(text)) {
                if (!responseBegin) {
                    session.println();
                    session.println();
                    session.println(new AttributedString(agentName+"->Result:", headerStyle).toAnsi());
                    responseBegin = true;
                    thinkingBegin = false;
                }
                session.print(text);
            }
        };
    }
}
