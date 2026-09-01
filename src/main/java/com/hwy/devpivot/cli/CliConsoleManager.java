package com.hwy.devpivot.cli;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过 ThreadLocal 管理 conversationId → ConsoleSession 的映射，
 * 供 LLMChatModelListener 等跨层组件按会话查找 ConsoleSession 实例。
 */
public class CliConsoleManager {
    private static final Map<String, ConsoleSession> holder = new ConcurrentHashMap<>();

    public static void register(String conversationId, ConsoleSession console) {
        holder.put(conversationId, console);
    }

    public static ConsoleSession get(String conversationId) {
        return holder.get(conversationId);
    }

    public static void remove(String conversationId) {
        holder.remove(conversationId);
    }
}
