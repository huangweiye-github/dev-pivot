package com.hwy.devpivot.cli;

/**
 * CLI 控制台会话契约 —— JLine 和 TamboUI 两种实现共享此接口，
 * 方便在不同 CLI 前端之间切换。
 */
public interface ConsoleSession {

    /** 启动 REPL / TUI 主循环，阻塞直到用户退出 */
    void start();

    /** 停止运行 */
    void stop();

    /** 当前会话 ID，多轮对话复用 */
    String getConversationId();

    /** 输出文本（不换行） */
    void print(String text);

    /** 输出空行 */
    void println();

    /** 输出文本并换行 */
    void println(String text);

    /** 刷新输出缓冲区 */
    void flush();
}
