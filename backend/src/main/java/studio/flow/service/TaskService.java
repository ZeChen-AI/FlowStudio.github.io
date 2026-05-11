package studio.flow.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import studio.flow.config.FlowStudioProperties;
import studio.flow.model.EditTask;
import studio.flow.model.TaskStatus;
import studio.flow.runner.RunnerResult;
import studio.flow.runner.TaskRunner;

@Service
public class TaskService {
  private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "webm");
  private static final Set<String> MASK_EXTENSIONS = Set.of("png", "jpg", "jpeg");

  private final FlowStudioProperties properties;
  private final TaskRunner runner;
  private final Map<String, EditTask> tasks = new ConcurrentHashMap<>();

  public TaskService(FlowStudioProperties properties, TaskRunner runner) {
    this.properties = properties;
    this.runner = runner;
  }

  public EditTask createEditTask(
      String projectName,
      String sourcePrompt,
      String targetPrompt,
      String targetWord,
      MultipartFile video,
      MultipartFile mask)
      throws IOException {
    validateText(targetPrompt, "targetPrompt is required.");
    validateText(targetWord, "targetWord is required.");
    validateFile(video, VIDEO_EXTENSIONS, "video");
    validateFile(mask, MASK_EXTENSIONS, "mask");

    String taskId = newTaskId();
    Path taskDir = properties.getRuntimeDir().resolve("tasks").resolve(taskId).toAbsolutePath().normalize();
    Files.createDirectories(taskDir);

    EditTask task = new EditTask(taskId);
    task.setProjectName(defaultProjectName(projectName));
    task.setSourcePrompt(nullToEmpty(sourcePrompt));
    task.setTargetPrompt(targetPrompt.trim());
    task.setTargetWord(targetWord.trim());
    task.setTaskDir(taskDir);
    task.setInputVideoPath(saveMultipart(video, taskDir, "input", VIDEO_EXTENSIONS));
    task.setMaskPath(saveMultipart(mask, taskDir, "mask", MASK_EXTENSIONS));
    task.setMessage("Task created and waiting for runner.");
    tasks.put(taskId, task);

    CompletableFuture.runAsync(() -> runTask(taskId));
    return task;
  }

  public Optional<EditTask> find(String taskId) {
    return Optional.ofNullable(tasks.get(taskId));
  }

  public Path resolveTaskFile(String taskId, String fileName) {
    EditTask task = requireTask(taskId);
    Path candidate = task.getTaskDir().resolve(fileName).normalize();
    if (!candidate.startsWith(task.getTaskDir()) || !Files.exists(candidate)) {
      throw new IllegalArgumentException("File not found.");
    }
    return candidate;
  }

  private void runTask(String taskId) {
    EditTask task = requireTask(taskId);
    task.setStatus(TaskStatus.RUNNING);
    task.setMessage("Runner is processing the video.");
    System.out.println("[FlowStudio] Task " + taskId + " started runner.");

    try {
      RunnerResult result = runner.run(task);
      if (!result.success()) {
        fail(task, result.message());
        return;
      }

      task.setStatus(TaskStatus.SUCCESS);
      task.setResultVideoPath(result.resultPath());
      task.setResultUrl("/api/files/" + task.getTaskId() + "/" + result.resultPath().getFileName());
      task.setMessage(result.message());
      task.setErrorMessage(null);
    } catch (Exception error) {
      fail(task, error.getMessage());
    }
  }

  private EditTask requireTask(String taskId) {
    EditTask task = tasks.get(taskId);
    if (task == null) {
      throw new IllegalArgumentException("Task not found: " + taskId);
    }
    return task;
  }

  private void fail(EditTask task, String message) {
    task.setStatus(TaskStatus.FAILED);
    task.setErrorMessage(message == null || message.isBlank() ? "Task failed." : message);
    task.setMessage("Task failed.");
  }

  private void validateText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }

  private void validateFile(MultipartFile file, Set<String> extensions, String label) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException(label + " file is required.");
    }
    String extension = extensionOf(file.getOriginalFilename());
    if (!extensions.contains(extension)) {
      throw new IllegalArgumentException(label + " file type is not supported.");
    }
  }

  private Path saveMultipart(MultipartFile file, Path taskDir, String baseName, Set<String> extensions)
      throws IOException {
    String extension = extensionOf(file.getOriginalFilename());
    if (!extensions.contains(extension)) {
      throw new IllegalArgumentException("Unsupported file type.");
    }
    Path output = taskDir.resolve(baseName + "." + extension).normalize();
    if (!output.startsWith(taskDir)) {
      throw new IllegalArgumentException("Invalid file path.");
    }
    file.transferTo(output);
    return output;
  }

  private String extensionOf(String originalName) {
    String cleanName = StringUtils.cleanPath(originalName == null ? "" : originalName);
    int dot = cleanName.lastIndexOf('.');
    return dot >= 0 ? cleanName.substring(dot + 1).toLowerCase() : "";
  }

  private String defaultProjectName(String projectName) {
    if (projectName == null || projectName.isBlank()) {
      return "FlowStudio " + DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now());
    }
    return projectName.trim();
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private String newTaskId() {
    return "task-" + UUID.randomUUID().toString().substring(0, 8);
  }
}
