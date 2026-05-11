package studio.flow.controller;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import studio.flow.dto.ResultResponse;
import studio.flow.dto.TaskResponse;
import studio.flow.model.EditTask;
import studio.flow.model.TaskStatus;
import studio.flow.service.TaskService;

@RestController
@RequestMapping("/api")
public class TaskController {
  private final TaskService taskService;

  public TaskController(TaskService taskService) {
    this.taskService = taskService;
  }

  @PostMapping(value = "/tasks/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public TaskResponse createTask(
      @RequestParam(value = "projectName", required = false) String projectName,
      @RequestParam(value = "sourcePrompt", required = false) String sourcePrompt,
      @RequestParam("targetPrompt") String targetPrompt,
      @RequestParam("targetWord") String targetWord,
      @RequestParam("video") MultipartFile video,
      @RequestParam("mask") MultipartFile mask)
      throws IOException {
    EditTask task = taskService.createEditTask(projectName, sourcePrompt, targetPrompt, targetWord, video, mask);
    return TaskResponse.from(task);
  }

  @GetMapping("/tasks/{taskId}")
  public TaskResponse getTask(@PathVariable String taskId) {
    return TaskResponse.from(taskService.find(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found.")));
  }

  @GetMapping("/tasks/{taskId}/status")
  public TaskResponse getStatus(@PathVariable String taskId) {
    return getTask(taskId);
  }

  @GetMapping("/tasks/{taskId}/result")
  public ResultResponse getResult(@PathVariable String taskId) {
    EditTask task = taskService.find(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found."));
    if (task.getStatus() != TaskStatus.SUCCESS || task.getResultUrl() == null) {
      throw new IllegalArgumentException("Task result is not ready.");
    }
    return new ResultResponse(task.getTaskId(), task.getResultUrl());
  }

  @GetMapping("/files/{taskId}/{fileName}")
  public ResponseEntity<Resource> getFile(@PathVariable String taskId, @PathVariable String fileName) {
    Path file = taskService.resolveTaskFile(taskId, fileName);
    FileSystemResource resource = new FileSystemResource(file);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFileName() + "\"")
        .contentType(mediaType(file))
        .body(resource);
  }

  private MediaType mediaType(Path file) {
    String name = file.getFileName().toString().toLowerCase();
    if (name.endsWith(".mp4")) {
      return MediaType.valueOf("video/mp4");
    }
    if (name.endsWith(".webm")) {
      return MediaType.valueOf("video/webm");
    }
    if (name.endsWith(".png")) {
      return MediaType.IMAGE_PNG;
    }
    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
      return MediaType.IMAGE_JPEG;
    }
    return MediaType.APPLICATION_OCTET_STREAM;
  }
}
