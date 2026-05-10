package studio.flow.dto;

import java.time.Instant;
import studio.flow.model.EditTask;
import studio.flow.model.TaskStatus;

public record TaskResponse(
    String taskId,
    String projectName,
    String sourcePrompt,
    String targetPrompt,
    TaskStatus status,
    String resultUrl,
    String errorMessage,
    String message,
    Instant createdAt) {
  public static TaskResponse from(EditTask task) {
    return new TaskResponse(
        task.getTaskId(),
        task.getProjectName(),
        task.getSourcePrompt(),
        task.getTargetPrompt(),
        task.getStatus(),
        task.getResultUrl(),
        task.getErrorMessage(),
        task.getMessage(),
        task.getCreatedAt());
  }
}
