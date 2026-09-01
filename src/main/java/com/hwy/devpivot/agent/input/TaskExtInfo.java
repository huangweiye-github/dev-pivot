package com.hwy.devpivot.agent.input;

import dev.langchain4j.model.output.structured.Description;

public class TaskExtInfo {
    private String taskId;
    private Integer taskIdx;
    private String taskTitle;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getTaskIdx() {
        return taskIdx;
    }

    public void setTaskIdx(Integer taskIdx) {
        this.taskIdx = taskIdx;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
    }
}
