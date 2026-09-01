package com.hwy.devpivot.cli;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 输入收集器 —— 聚合所有用户输入，统一管理并提供查询 / 打印能力。
 *
 * <p>用法：
 * <pre>{@code
 *   InputCollector collector = new InputCollector();
 *   collector.add("hello", UserInput.InputType.SINGLE_LINE);
 *   collector.add("def foo():\n    pass", UserInput.InputType.MULTI_LINE);
 *   collector.printAll();
 * }</pre>
 */
public class InputCollector {

    private final List<UserInput> inputs    = new ArrayList<>();
    private final String          sessionId;
    private final LocalDateTime   startTime;
    private       int             idCounter = 0;

    public InputCollector() {
        this.sessionId = java.util.UUID.randomUUID().toString().substring(0, 8);
        this.startTime = LocalDateTime.now();
    }

    // ----------------------------------------------------------------
    //  添加
    // ----------------------------------------------------------------

    /**
     * 添加一条用户输入。
     *
     * @param text 原始输入文本（可能包含换行）
     * @param type 输入类型
     * @return 生成的 {@link UserInput} 对象
     */
    public UserInput add(String text, UserInput.InputType type) {
        UserInput ui = new UserInput(++idCounter, text, type);
        inputs.add(ui);
        return ui;
    }

    /**
     * 自动推断类型后添加。
     * 规则：含换行 → MULTI_LINE；以 / 开头 → SLASH_COMMAND；否则 SINGLE_LINE。
     */
    public UserInput addAuto(String text) {
        UserInput.InputType type;
        if (text.contains("\n") || text.contains("\r")) {
            type = UserInput.InputType.MULTI_LINE;
        } else if (text.startsWith("/")) {
            type = UserInput.InputType.SLASH_COMMAND;
        } else {
            type = UserInput.InputType.SINGLE_LINE;
        }
        return add(text, type);
    }

    // ----------------------------------------------------------------
    //  查询
    // ----------------------------------------------------------------

    /** 获取所有输入（不可变视图）。 */
    public List<UserInput> getAll() {
        return Collections.unmodifiableList(inputs);
    }

    /** 最近一次输入，没有则返回 {@code null}。 */
    public UserInput getLast() {
        return inputs.isEmpty() ? null : inputs.get(inputs.size() - 1);
    }

    /** 当前已收集的输入条数。 */
    public int count() {
        return inputs.size();
    }

    public String getSessionId()    { return sessionId; }
    public LocalDateTime getStartTime() { return startTime; }

    // ----------------------------------------------------------------
    //  输出
    // ----------------------------------------------------------------

    /** 逐条打印所有输入到指定输出流。 */
    public void printAll(PrintWriter out) {
        if (inputs.isEmpty()) {
            out.println("[No user input]");
            out.flush();
            return;
        }
        out.println("\n+============================================+");
        out.println("|         User Input Summary                  |");
        out.println("+============================================+");
        out.printf("| Session: %-34s |\n", sessionId);
        out.printf("| Count:   %-34d |\n", inputs.size());
        out.println("+============================================+\n");
        for (UserInput ui : inputs) {
            out.println(ui);
        }
        out.flush();
    }

    /** 清除所有已收集的输入。 */
    public void clear() {
        inputs.clear();
        idCounter = 0;
    }
}
