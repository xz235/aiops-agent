package com.resume.aiops.memory;

import com.resume.aiops.memory.domain.ToolCallRecord;
import java.util.List;

public interface ToolCallRepository {
    void append(ToolCallRecord record);
    List<ToolCallRecord> findByTaskId(String taskId);
}

