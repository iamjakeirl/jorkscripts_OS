package com.jork.script.jorkHunter.utils.tasks;

import com.osmb.api.script.Script;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskManager {

    private final Script script;
    private final List<Task> tasks;

    public TaskManager(Script script) {
        this.script = script;
        this.tasks = new ArrayList<>();
    }

    public void addTasks(Task... tasks) {
        Collections.addAll(this.tasks, tasks);
    }

    public int executeNextTask() {
        for (Task task : tasks) {
            if (task.canExecute()) {
                return task.execute();
            }
        }
        return 1000; // Default delay if no task is executed
    }

    public List<Task> getTasks() {
        return tasks;
    }
    
    public void clearTasks() {
        tasks.clear();
    }
} 