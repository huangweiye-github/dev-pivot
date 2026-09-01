package com.hwy.devpivot.agent.tools;

import com.hwy.devpivot.env.ModelConfig;
import com.hwy.devpivot.env.SettingsReader;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebFetchTool implements DevPivotTool {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private static final int MAX_BODY = 200_000;
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final int CACHE_TTL_MIN = 15;

    /** 用于处理 prompt 的轻量模型，延迟初始化 */
    private static volatile OpenAiChatModel processingModel;
    private static volatile boolean modelInitFailed;

    static {
        Thread cleanup = new Thread(() -> {
            while (true) {
                try { Thread.sleep(60_000); } catch (InterruptedException e) { break; }
                long now = System.currentTimeMillis();
                CACHE.entrySet().removeIf(e -> now - e.getValue().ts > CACHE_TTL_MIN * 60_000L);
            }
        }, "webfetch-cache-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    @Tool(name = "WebFetch", value = "获取指定URL的网页内容并转换为markdown格式，再使用AI模型根据prompt提取信息。必填: url, prompt。自带15分钟缓存。认证/私有页面将失败。")
    public String webFetch(
            @P("要获取的URL地址，必须是完整URL") String url,
            @P("描述你想从页面中提取什么信息，会传给AI模型处理") String prompt) {

        if (url == null || url.isBlank()) return "Error: url 为必填参数";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        String cacheKey = url + "|" + (prompt != null ? prompt : "");
        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.ts < CACHE_TTL_MIN * 60_000L) {
            return "(cached)\n" + cached.content;
        }

        try {
            // 1. HTTP 请求
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();

            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());

            // 检查重定向
            int status = resp.statusCode();
            if (status >= 300 && status < 400) {
                String location = resp.headers().firstValue("Location").orElse(resp.headers().firstValue("location").orElse(""));
                if (!location.isBlank()) {
                    return "Redirect: " + location + "\n请使用此 URL 重新发起 WebFetch 请求。";
                }
            }

            byte[] raw = resp.body();
            if (raw == null || raw.length == 0) return "Error: 响应内容为空 (status=" + status + ")";

            String contentType = resp.headers().firstValue("Content-Type").orElse("");
            Charset charset = detectCharset(contentType, raw);

            // 2. HTML → Markdown
            String html = new String(raw, charset);
            String markdown = htmlToMarkdown(html, url);

            if (markdown.isBlank()) {
                return "Error: 解析后内容为空，原响应长度=" + raw.length + " bytes, Content-Type=" + contentType;
            }

            if (markdown.length() > MAX_BODY) {
                markdown = markdown.substring(0, MAX_BODY) + "\n\n... [内容截断，已显示前 " + MAX_BODY + " 字符]";
            }

            // 3. 用模型处理 prompt
            String result;
            if (prompt != null && !prompt.isBlank()) {
                result = processWithModel(markdown, prompt, url);
            } else {
                result = markdown;
            }

            String output = "URL: " + url + "\nStatus: " + status + "\n" +
                    "─".repeat(60) + "\n" + result;

            CACHE.put(cacheKey, new CacheEntry(output, System.currentTimeMillis()));
            return output;

        } catch (IOException e) {
            return "Error: 网络请求失败: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: 请求被中断";
        } catch (Exception e) {
            return "Error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    // ── HTML → Markdown (Jsoup) ────────────────────────

    private static String htmlToMarkdown(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        doc.select("script, style, noscript, nav, footer, iframe, svg, canvas, input, textarea, select, button").remove();
        Element body = doc.body();
        if (body == null) body = doc;

        StringBuilder sb = new StringBuilder();
        NodeTraversor.traverse(new MdVisitor(sb, baseUrl), body);
        // 清理多余空行
        return sb.toString().replaceAll("\n{4,}", "\n\n\n").strip();
    }

    private static class MdVisitor implements NodeVisitor {
        private final StringBuilder sb;
        private final String baseUrl;
        private int listDepth;

        MdVisitor(StringBuilder sb, String baseUrl) { this.sb = sb; this.baseUrl = baseUrl; }

        @Override
        public void head(Node node, int depth) {
            if (node instanceof TextNode) {
                String text = ((TextNode) node).getWholeText();
                if (!text.isBlank()) sb.append(text);
                return;
            }
            if (!(node instanceof Element el)) return;
            String tag = el.tagName();

            switch (tag) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    int level = tag.charAt(1) - '0';
                    sb.append("\n\n").append("#".repeat(level)).append(" ");
                }
                case "p" -> sb.append("\n\n");
                case "br" -> sb.append("\n");
                case "hr" -> sb.append("\n\n──\n\n");
                case "li" -> {
                    sb.append("\n");
                    String prefix = el.parent() != null && el.parent().tagName().equals("ol") ? "1. " : "- ";
                    sb.append("  ".repeat(Math.max(0, listDepth - 1))).append(prefix);
                }
                case "blockquote" -> sb.append("\n\n> ");
                case "strong", "b" -> sb.append("**");
                case "em", "i" -> sb.append("*");
                case "code" -> sb.append("`");
                case "pre" -> sb.append("\n\n```\n");
                case "a" -> {
                    String href = el.absUrl("href");
                    if (!href.isEmpty()) sb.append("[");
                }
                case "img" -> {
                    String src = el.absUrl("src");
                    String alt = el.attr("alt");
                    if (!src.isEmpty()) sb.append("\n\n![").append(alt).append("](").append(src).append(")\n\n");
                }
                case "ul", "ol" -> {
                    sb.append("\n");
                    listDepth++;
                }
                case "table" -> sb.append("\n\n");
                case "tr" -> sb.append("\n| ");
                case "td", "th" -> sb.append(" | ");
            }
        }

        @Override
        public void tail(Node node, int depth) {
            if (!(node instanceof Element el)) return;
            String tag = el.tagName();

            switch (tag) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> sb.append("\n");
                case "p" -> sb.append("\n");
                case "blockquote" -> sb.append("\n");
                case "strong", "b" -> sb.append("**");
                case "em", "i" -> sb.append("*");
                case "code" -> sb.append("`");
                case "pre" -> sb.append("\n```\n");
                case "a" -> {
                    String href = el.absUrl("href");
                    if (!href.isEmpty()) sb.append("](").append(href).append(")");
                }
                case "ul", "ol" -> {
                    listDepth--;
                    sb.append("\n");
                }
                case "tr" -> sb.append(" |");
            }
        }
    }

    // ── charset 检测 ──────────────────────────────────

    private static final Pattern CHARSET_HEADER = Pattern.compile("charset\\s*=\\s*([^;\\s]+)", Pattern.CASE_INSENSITIVE);

    private static Charset detectCharset(String contentType, byte[] raw) {
        // 1. Content-Type header
        if (contentType != null) {
            Matcher m = CHARSET_HEADER.matcher(contentType);
            if (m.find()) {
                try { return Charset.forName(aliasCharset(m.group(1))); } catch (Exception ignored) {}
            }
        }
        // 2. BOM 检测
        if (raw.length >= 3 && (raw[0] & 0xFF) == 0xEF && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF)
            return StandardCharsets.UTF_8;
        if (raw.length >= 2) {
            if ((raw[0] & 0xFF) == 0xFE && (raw[1] & 0xFF) == 0xFF) return StandardCharsets.UTF_16BE;
            if ((raw[0] & 0xFF) == 0xFF && (raw[1] & 0xFF) == 0xFE) return StandardCharsets.UTF_16LE;
        }
        // 3. HTML meta 检测（用 ASCII 安全前缀扫描）
        String probe = new String(raw, 0, Math.min(raw.length, 2000), StandardCharsets.ISO_8859_1);
        Matcher metaMatcher = Pattern.compile("<meta[^>]+charset\\s*=\\s*[\"']?([^\"'\\s>;]+)", Pattern.CASE_INSENSITIVE).matcher(probe);
        if (metaMatcher.find()) {
            try { return Charset.forName(aliasCharset(metaMatcher.group(1))); } catch (Exception ignored) {}
        }
        return StandardCharsets.UTF_8;
    }

    private static String aliasCharset(String cs) {
        return switch (cs.toLowerCase()) {
            case "gb2312" -> "GBK";
            case "iso-8859-1", "latin1" -> "ISO-8859-1";
            default -> cs;
        };
    }

    // ── AI 模型处理 ────────────────────────────────────

    private static String processWithModel(String markdown, String prompt, String url) {
        OpenAiChatModel model = getOrCreateModel();
        if (model == null) {
            // 无模型可用时，返回完整 markdown（标注无AI处理）
            return "[注] 无可用模型，返回原始内容:\n\n" + markdown;
        }
        try {
            String systemMsg = "你是一个网页内容提取助手。根据用户prompt从提供的markdown内容中提取或总结相关信息。" +
                    "直接返回提取结果，不要添加前缀或解释。";
            String userMsg = "URL: " + url + "\n\n用户prompt: " + prompt + "\n\n网页内容(markdown):\n" + markdown;
            List<ChatMessage> messages = List.of(
                    new SystemMessage(systemMsg),
                    new UserMessage(userMsg)
            );
            ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();
            String response = model.doChat(chatRequest).aiMessage().text();
            return response != null && !response.isBlank() ? response : markdown;
        } catch (Exception e) {
            return "[模型处理失败: " + e.getMessage() + "]\n\n" + markdown;
        }
    }

    private static synchronized OpenAiChatModel getOrCreateModel() {
        if (processingModel != null) return processingModel;
        if (modelInitFailed) return null;
        try {
            ModelConfig cfg = SettingsReader.getModelConfig();
            String apiKey = cfg.getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                modelInitFailed = true;
                return null;
            }
            processingModel = OpenAiChatModel.builder()
                    .baseUrl(cfg.getBaseUrl())
                    .apiKey(apiKey)
                    .modelName(cfg.getModelName())
                    .temperature(0.1)
                    .logRequests(false)
                    .logResponses(false)
                    .build();
            return processingModel;
        } catch (Exception e) {
            modelInitFailed = true;
            return null;
        }
    }

    private static class CacheEntry {
        final String content;
        final long ts;
        CacheEntry(String content, long ts) { this.content = content; this.ts = ts; }
    }
}
