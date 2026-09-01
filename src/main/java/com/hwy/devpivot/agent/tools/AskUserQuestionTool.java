package com.hwy.devpivot.agent.tools;

import com.alibaba.fastjson.JSON;
import com.hwy.devpivot.cli.CliConsoleManager;
import com.hwy.devpivot.cli.ConsoleSession;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.model.output.structured.Description;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class AskUserQuestionTool implements DevPivotTool {

    private final AttributedStyle headerStyle = new AttributedStyle().foreground(AttributedStyle.CYAN).bold();

    @Tool(name = "AskUserQuestion", value = """
              需要向用户提出问题、需要用户回答、需要用户选择某个选项时调用此工具
            """)
    public String ask(
        @P("问题列表") List<Question> questions ,@ToolMemoryId String toolMemoryId) {

        if (questions == null || questions.isEmpty()) {
            return "Error: questions 不能为空";
        }
        ConsoleSession consoleSession = CliConsoleManager.get(toolMemoryId);
        consoleSession.println(new AttributedString("AskQuestion:", headerStyle).toAnsi());
        Map<String, Object> answers = new LinkedHashMap<>();
        Scanner scanner = new Scanner(System.in);
        int total = questions.size();

        for (int i = 0; i < total; i++) {
            Question q = questions.get(i);
            if (q.question == null || q.question.isBlank()) continue;
            if (q.options == null || q.options.size() < 2) continue;

            int optCount = q.options.size();

            consoleSession.println();
            printHeader(i + 1, total, q.question,consoleSession);

            for (int j = 0; j < optCount; j++) {
                Option opt = q.options.get(j);
                consoleSession.println(String.format("  [%d] %s", j + 1, opt.label != null ? opt.label : ""));
                if (opt.description != null && !opt.description.isBlank()) {
                    consoleSession.println(String.format(" — %s", opt.description));
                }
                consoleSession.println();
            }

            if (Boolean.TRUE.equals(q.multiSelect)) {
                consoleSession.print("多选（逗号分隔，如 1,3）：");
            } else {
                consoleSession.print("请选择（1-" + optCount + "）：");
            }

            String input = scanner.nextLine().trim();
            String key = q.header != null && !q.header.isBlank() ? q.header : "q" + (i + 1);

            if (Boolean.TRUE.equals(q.multiSelect)) {
                List<String> selected = new ArrayList<>();
                for (String part : input.split("[,\\s]+")) {
                    try {
                        int idx = Integer.parseInt(part) - 1;
                        if (idx >= 0 && idx < optCount) {
                            selected.add(q.options.get(idx).label);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                answers.put(key, selected);
            } else {
                try {
                    int idx = Integer.parseInt(input) - 1;
                    if (idx >= 0 && idx < optCount) {
                        answers.put(key, q.options.get(idx).label);
                    } else {
                        answers.put(key, "(无效选择)");
                    }
                } catch (NumberFormatException e) {
                    answers.put(key, input);
                }
            }
        }

        return JSON.toJSONString(answers);
    }

    private void printHeader(int index, int total, String question, ConsoleSession consoleSession ) {
        String prefix = index + "/" + total + " ";
        consoleSession.println(prefix + question);
    }

    // ── POJO ──────────────────────────────────────────

    public static class Question {
        @Description("向用户提问的问题文本")
        public String question;
        @Description("问题的简短标签")
        public String header;
        @Description("是否允许多选，Y为多选，N为单选")
        public String multiSelect;
        @Description("可选项列表")
        public List<Option> options;
    }

    public static class Option {
        @Description("选项的简短文本")
        public String label;
        @Description("选项的描述信息，帮助用户理解该选项的含义")
        public String description;
    }
}
