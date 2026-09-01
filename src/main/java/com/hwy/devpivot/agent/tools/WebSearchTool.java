package com.hwy.devpivot.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSearchTool implements DevPivotTool {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private static final int MAX_RESULTS = 10;

    // 百度搜索结果: <div class="result c-container"> ... <h3><a href="...">Title</a></h3> ... <span class="content-right_...">Snippet</span>
    private static final Pattern RESULT_BLOCK = Pattern.compile(
            "<div[^>]*class=\"[^\"]*result[^\"]*c-container[^\"]*\"[^>]*>(.*?)</div>\\s*</div>\\s*</div>",
            Pattern.DOTALL);
    private static final Pattern LINK_A = Pattern.compile(
            "<a[^>]*href\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern C_ABSTRACT = Pattern.compile(
            "<(span|div)[^>]*class=\"[^\"]*c-abstract[^\"]*\"[^>]*>(.*?)</(span|div)>", Pattern.DOTALL);
    private static final Pattern CONTENT_RIGHT = Pattern.compile(
            "<span[^>]*class=\"[^\"]*content-right[^\"]*\"[^>]*>(.*?)</span>", Pattern.DOTALL);

    @Tool(name = "WebSearch", value = "搜索互联网并返回结果链接和摘要。必填: query。可选: allowed_domains(包含这些域名)/blocked_domains(排除这些域名)。默认返回10条。")
    public String webSearch(
            @P("搜索关键词") String query,
            @P("允许的域名列表，逗号分隔") String allowed_domains,
            @P("禁止的域名列表，逗号分隔") String blocked_domains) {

        if (query == null || query.isBlank()) return "Error: query 为必填参数";

        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = "https://www.baidu.com/s?wd=" + encoded + "&ie=utf-8";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();

            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            byte[] raw = resp.body();
            if (raw == null || raw.length == 0) return "Error: 搜索返回为空 (status=" + resp.statusCode() + ")";

            String contentType = resp.headers().firstValue("Content-Type").orElse("");
            Charset charset = detectCharset(contentType, raw);
            String body = new String(raw, charset);

            List<String[]> results = parseBaiduResults(body);

            List<String> allowed = parseDomains(allowed_domains);
            List<String> blocked = parseDomains(blocked_domains);

            StringBuilder sb = new StringBuilder();
            sb.append("Query: ").append(query).append("\n");
            sb.append("─".repeat(60)).append("\n");

            int shown = 0;
            for (String[] r : results) {
                String url = r[0];
                String title = r[1];
                String snippet = r[2];

                String host = extractHost(url);
                if (host.isBlank()) continue;
                if (host.contains("baidu.com")) continue;
                if (!allowed.isEmpty() && allowed.stream().noneMatch(host::contains)) continue;
                if (blocked.stream().anyMatch(host::contains)) continue;

                shown++;
                sb.append("[").append(shown).append("] ");
                sb.append("[").append(title).append("](").append(url).append(")\n");
                sb.append("    ").append(snippet).append("\n\n");

                if (shown >= MAX_RESULTS) break;
            }

            if (shown == 0) sb.append("(无匹配结果)\n");
            return sb.toString();

        } catch (IOException e) {
            return "Error: 搜索请求失败: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: 搜索被中断";
        } catch (Exception e) {
            return "Error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    // ── 解析百度搜索结果 ───────────────────────────────

    private List<String[]> parseBaiduResults(String html) {
        List<String[]> results = new ArrayList<>();

        Matcher blockMatcher = RESULT_BLOCK.matcher(html);
        while (blockMatcher.find() && results.size() < MAX_RESULTS + 5) {
            String block = blockMatcher.group(1);
            if (block == null || block.isBlank()) continue;

            // 提取标题和链接
            String linkUrl = null, linkTitle = null;
            Matcher am = LINK_A.matcher(block);
            if (am.find()) {
                linkUrl = decodeHtml(am.group(1)).strip();
                linkTitle = stripTags(am.group(2)).strip();
            }
            if (linkUrl == null || linkTitle == null || linkTitle.isBlank()) continue;

            // 提取摘要
            String snippet = "";
            Matcher sm = C_ABSTRACT.matcher(block);
            if (sm.find()) {
                snippet = stripTags(sm.group(2)).strip();
            }
            if (snippet.isBlank()) {
                sm = CONTENT_RIGHT.matcher(block);
                if (sm.find()) {
                    snippet = stripTags(sm.group(1)).strip();
                }
            }

            results.add(new String[]{linkUrl, linkTitle, snippet});
        }

        return results;
    }

    // ── charset 检测 ──────────────────────────────────

    private static final Pattern CHARSET_HEADER = Pattern.compile("charset\\s*=\\s*([^;\\s]+)", Pattern.CASE_INSENSITIVE);

    private static Charset detectCharset(String contentType, byte[] raw) {
        if (contentType != null) {
            Matcher m = CHARSET_HEADER.matcher(contentType);
            if (m.find()) {
                try { return Charset.forName(aliasCharset(m.group(1))); } catch (Exception ignored) {}
            }
        }
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
            default -> cs;
        };
    }

    // ── 辅助方法 ───────────────────────────────────────

    private static String extractHost(String url) {
        try {
            URI u = URI.create(url);
            String host = u.getHost();
            return host != null ? host.toLowerCase() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> parseDomains(String csv) {
        List<String> list = new ArrayList<>();
        if (csv == null || csv.isBlank()) return list;
        for (String part : csv.split("[,\\s]+")) {
            String d = part.strip().toLowerCase();
            if (!d.isEmpty()) list.add(d);
        }
        return list;
    }

    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private static String stripTags(String s) {
        return TAG.matcher(s).replaceAll("");
    }

    private static String decodeHtml(String s) {
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ");
    }
}
