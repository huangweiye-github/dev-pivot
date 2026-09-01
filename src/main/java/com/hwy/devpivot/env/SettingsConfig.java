package com.hwy.devpivot.env;

/**
 * settings.json 顶层配置，包含 model 等配置节。
 */
public class SettingsConfig {

    private ModelConfig model = new ModelConfig();

    public ModelConfig getModel()     { return model; }
    public void setModel(ModelConfig model) { this.model = model; }
}
