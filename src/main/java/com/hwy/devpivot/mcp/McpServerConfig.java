package com.hwy.devpivot.mcp;

import java.util.List;
import java.util.Map;

/**
 * 单个 MCP Server 配置，映射 JSON 中 mcpServers.{name} 的结构。
 * 支持 stdio 和 streamableHttp 两种类型。
 */
public class McpServerConfig {

    /** stdio / http */
    private String type;

    /** stdio: 启动命令 */
    private String command;

    /** stdio: 命令参数列表 */
    private List<String> args;

    /** streamableHttp/http: 自定义请求头 */
    private Map<String, String> headers;

    /** stdio: 环境变量 */
    private Map<String, String> env;

    /** streamableHttp: MCP 服务 URL */
    private String url;

    /** streamableHttp: 超时秒数，默认 60 */
    private long timeout = 60;

    /** 通用: 是否记录事件/请求 */
    private boolean logEvents;
    private boolean logRequests;
    private boolean logResponses;

    // ── getters / setters ──────────────────────────────

    public String getType()                            { return type; }
    public void setType(String type)                   { this.type = type; }
    public String getCommand()                         { return command; }
    public void setCommand(String command)             { this.command = command; }
    public List<String> getArgs()                      { return args; }
    public void setArgs(List<String> args)             { this.args = args; }
    public Map<String, String> getHeaders()             { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    public Map<String, String> getEnv()                 { return env; }
    public void setEnv(Map<String, String> env)         { this.env = env; }
    public String getUrl()                             { return url; }
    public void setUrl(String url)                     { this.url = url; }
    public long getTimeout()                           { return timeout; }
    public void setTimeout(long timeout)               { this.timeout = timeout; }
    public boolean isLogEvents()                       { return logEvents; }
    public void setLogEvents(boolean logEvents)         { this.logEvents = logEvents; }
    public boolean isLogRequests()                     { return logRequests; }
    public void setLogRequests(boolean logRequests)     { this.logRequests = logRequests; }
    public boolean isLogResponses()                    { return logResponses; }
    public void setLogResponses(boolean logResponses)   { this.logResponses = logResponses; }

    public boolean isStdio() {
        return "stdio".equalsIgnoreCase(type);
    }

    public boolean isHttp() {
        return "http".equalsIgnoreCase(type);
    }
    public boolean isSSE() {
        return "sse".equalsIgnoreCase(type);
    }
}
