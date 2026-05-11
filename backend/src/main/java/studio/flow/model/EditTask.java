package studio.flow.model;

import java.nio.file.Path;
import java.time.Instant;

public class EditTask {
  private final String taskId;
  private final Instant createdAt;
  private String projectName;
  private String sourcePrompt;
  private String targetPrompt;
  private String targetWord;
  private TaskStatus status;
  private Path taskDir;
  private Path inputVideoPath;
  private Path maskPath;
  private Path resultVideoPath;
  private String resultUrl;
  private String errorMessage;
  private String message;

  public EditTask(String taskId) {
    this.taskId = taskId;
    this.createdAt = Instant.now();
    this.status = TaskStatus.PENDING;
  }

  public String getTaskId() {
    return taskId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  public String getSourcePrompt() {
    return sourcePrompt;
  }

  public void setSourcePrompt(String sourcePrompt) {
    this.sourcePrompt = sourcePrompt;
  }

  public String getTargetPrompt() {
    return targetPrompt;
  }

  public void setTargetPrompt(String targetPrompt) {
    this.targetPrompt = targetPrompt;
  }

  public String getTargetWord() {
    return targetWord;
  }

  public void setTargetWord(String targetWord) {
    this.targetWord = targetWord;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public void setStatus(TaskStatus status) {
    this.status = status;
  }

  public Path getTaskDir() {
    return taskDir;
  }

  public void setTaskDir(Path taskDir) {
    this.taskDir = taskDir;
  }

  public Path getInputVideoPath() {
    return inputVideoPath;
  }

  public void setInputVideoPath(Path inputVideoPath) {
    this.inputVideoPath = inputVideoPath;
  }

  public Path getMaskPath() {
    return maskPath;
  }

  public void setMaskPath(Path maskPath) {
    this.maskPath = maskPath;
  }

  public Path getResultVideoPath() {
    return resultVideoPath;
  }

  public void setResultVideoPath(Path resultVideoPath) {
    this.resultVideoPath = resultVideoPath;
  }

  public String getResultUrl() {
    return resultUrl;
  }

  public void setResultUrl(String resultUrl) {
    this.resultUrl = resultUrl;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
