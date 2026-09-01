package com.hwy.devpivot.cli;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 用户输入模型 —— 封装单次用户输入的全部信息。
 */
public class UserInput {

    /** 输入类型 */
    public enum InputType {
        /** 单行输入 */
        SINGLE_LINE,
        /** 多行输入（Shift+Enter 换行 或 粘贴多行） */
        MULTI_LINE,
        /** 斜杠命令（以 / 开头） */
        SLASH_COMMAND
    }

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final int         id;
    private final String      text;
    private final LocalDateTime timestamp;
    private final InputType   inputType;

    public UserInput(int id, String text, InputType inputType) {
        this.id = id;
        this.text = text;
        this.timestamp = LocalDateTime.now();
        this.inputType = inputType;
    }

    public int getId()                  { return id; }
    public String getText()             { return text; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public InputType getInputType()     { return inputType; }

    /** 输入是否为空（仅空白字符） */
    public boolean isBlank() {
        return text == null || text.isBlank();
    }

    /** 输入包含的行数 */
    public int lineCount() {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\\R", -1).length;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=======================================\n");
        sb.append(String.format("  ID   : %d\n", id));
        sb.append(String.format("  Time : %s\n", timestamp.format(TF)));
        sb.append(String.format("  Type : %s", inputType));
        if (lineCount() > 1) {
            sb.append(String.format(" (%d lines)", lineCount()));
        }
        sb.append("\n");
        sb.append("---------------------------------------\n");
        sb.append(text);
        if (!text.endsWith("\n")) sb.append("\n");
        sb.append("=======================================");
        return sb.toString();
    }
}
