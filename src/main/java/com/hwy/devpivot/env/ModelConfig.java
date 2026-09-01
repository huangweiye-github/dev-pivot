package com.hwy.devpivot.env;

/**
 * AI 模型配置。
 */
public class ModelConfig {

    private String baseUrl    = "https://api.deepseek.com";
    private String apiKey     = "";
    private String modelName  = "deepseek-v4-pro";
    private double temperature = 0.7;
    private boolean logRequests  = false;
    private boolean logResponses = false;

    public String getBaseUrl()     { return baseUrl; }
    public String getApiKey()      { return apiKey != null && !apiKey.isBlank() ? apiKey : System.getenv("DEV_PIVOT_API_KEY"); }
    public String getModelName()   { return modelName; }
    public double getTemperature() { return temperature; }
    public boolean isLogRequests() { return logRequests; }
    public boolean isLogResponses(){ return logResponses; }

    public void setBaseUrl(String baseUrl)          { this.baseUrl = baseUrl; }
    public void setApiKey(String apiKey)            { this.apiKey = apiKey; }
    public void setModelName(String modelName)      { this.modelName = modelName; }
    public void setTemperature(double temperature)  { this.temperature = temperature; }
    public void setLogRequests(boolean logRequests) { this.logRequests = logRequests; }
    public void setLogResponses(boolean logResponses){ this.logResponses = logResponses; }
}
