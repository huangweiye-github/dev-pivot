package com.hwy.devpivot.agent.config;

/**
 * loop agent 的退出条件定义。
 */
public class ExitConditionDef {

    /** 条件类型：stateFlagCheck */
    private String type;
    /** 检查的 state key */
    private String stateKey;
    /** 读取的字段名 */
    private String field;
    /** 期望值，如 "Y" */
    private String expectedValue;
    /** true 表示取反（field != expectedValue 时退出） */
    private boolean negate;

    public String getType()              { return type; }
    public void setType(String type)     { this.type = type; }
    public String getStateKey()          { return stateKey; }
    public void setStateKey(String key)  { this.stateKey = key; }
    public String getField()             { return field; }
    public void setField(String field)   { this.field = field; }
    public String getExpectedValue()     { return expectedValue; }
    public void setExpectedValue(String v) { this.expectedValue = v; }
    public boolean isNegate()            { return negate; }
    public void setNegate(boolean negate){ this.negate = negate; }
}
