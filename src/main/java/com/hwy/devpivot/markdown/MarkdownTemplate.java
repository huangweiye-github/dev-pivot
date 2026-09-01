package com.hwy.devpivot.markdown;

import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.formatter.Formatter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.sequence.BasedSequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于 flexmark AST 的 Markdown 模板占位符替换工具。
 * <p>
 * 支持 {@code {{key}}} 格式的占位符。通过解析 markdown 为 AST，
 * 在 Text 节点上做精确替换，而非全局正则替换，避免破坏 markdown 语法结构。
 * </p>
 *
 * <pre>{@code
 * Map<String, String> values = Map.of("name", "World", "count", "42");
 * String result = MarkdownTemplate.replace("Hello **{{name}}**, count={{count}}", values);
 * // => "Hello **World**, count=42"
 * }</pre>
 */
public class MarkdownTemplate {

    /** 匹配 {{key}} 占位符，key 仅限字母、数字、下划线、短横线 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([\\w-]+)\\}\\}");

    /** flexmark parser，线程安全 */
    private static final Parser PARSER = Parser.builder().build();

    /** flexmark formatter，线程安全 */
    private static final Formatter FORMATTER = Formatter.builder().build();

    private MarkdownTemplate() {
    }

    /**
     * 替换 markdown 字符串中的 {@code {{key}}} 占位符。
     * <p>
     * 通过 flexmark 将 markdown 解析为 AST，遍历所有 {@link Text} 节点进行
     * 占位符替换，最后通过 formatter 渲染回 markdown 字符串。
     * </p>
     *
     * @param markdown 原始 markdown 文本，可为 null
     * @param values   占位符 key → 替换值的映射，可为 null
     * @return 替换后的 markdown 文本；若 markdown 为 null 或空，原样返回
     */
    public static String replace(String markdown, Map<String, String> values) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }
        if (values == null || values.isEmpty()) {
            return markdown;
        }

        Document doc = PARSER.parse(markdown);
        walkAndReplace(doc, values);
        return FORMATTER.render(doc);
    }

    /**
     * 从 classpath 资源文件读取 markdown 模板，替换占位符后返回。
     *
     * @param resourcePath 资源路径（相对于 classpath 根），如 {@code "prompt/coding-agent.md"}
     * @param values       占位符 key → 替换值的映射
     * @return 替换后的 markdown 文本
     * @throws IllegalArgumentException 资源文件不存在时抛出
     * @throws RuntimeException         读取文件发生 IO 错误时抛出
     */
    public static String replaceFromResource(String resourcePath, Map<String, String> values) {
        InputStream is = MarkdownTemplate.class.getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String markdown = reader.lines().collect(Collectors.joining("\n"));
            return replace(markdown, values);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + resourcePath, e);
        }
    }

    /**
     * 递归遍历 AST，对 Text 节点中的占位符进行替换。
     * <p>
     * 替换方式：创建一个新的 Text 节点插入到原节点之前，然后从 AST 中移除原节点。
     * 这样可以保证 AST 结构完整，避免直接修改 BasedSequence 带来的不一致问题。
     * </p>
     */
    private static void walkAndReplace(Node node, Map<String, String> values) {
        if (node instanceof Text text) {
            String original = text.getChars().toString();
            String replaced = replacePlaceholders(original, values);
            if (!replaced.equals(original)) {
                Text newText = new Text(BasedSequence.of(replaced));
                text.insertBefore(newText);
                text.unlink();
            }
        }

        // 递归处理子节点，先取 next 再递归，防止子节点被替换后链表断裂
        Node child = node.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            walkAndReplace(child, values);
            child = next;
        }
    }

    /**
     * 对单个文本片段进行占位符替换。
     */
    private static String replacePlaceholders(String text, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
