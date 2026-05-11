package studio.flow.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import studio.flow.config.FlowStudioProperties;
import studio.flow.model.EditTask;

@Component
@ConditionalOnProperty(prefix = "flowstudio", name = "mock-runner", havingValue = "false")
public class AutodlTaskRunner implements TaskRunner {
  private final FlowStudioProperties properties;
  private final RestClient restClient;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public AutodlTaskRunner(FlowStudioProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.restClient = RestClient.builder().build();
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    this.objectMapper = objectMapper;
  }

  @Override
  public RunnerResult run(EditTask task) throws Exception {
    if (properties.getAutodlBaseUrl() == null || properties.getAutodlBaseUrl().isBlank()) {
      throw new IllegalStateException("AUTODL_BASE_URL is required when mock runner is disabled.");
    }

    System.out.println("[FlowStudio] Calling AutoDL: " + normalizeBaseUrl() + "/edit");
    String responseText = postMultipart(task);
    Map<String, Object> response = objectMapper.readValue(responseText, new TypeReference<>() {});

    boolean success = Boolean.TRUE.equals(response.get("success"));
    String message = String.valueOf(response.getOrDefault("message", ""));
    if (!success) {
      return new RunnerResult(false, null, message.isBlank() ? "AutoDL edit failed." : message);
    }

    Object resultPathValue = response.get("resultPath");
    if (resultPathValue == null || String.valueOf(resultPathValue).isBlank()) {
      return new RunnerResult(false, null, "AutoDL succeeded but did not return resultPath.");
    }

    Path output = task.getTaskDir().resolve("result.mp4");
    copyResult(String.valueOf(resultPathValue), output);
    return new RunnerResult(true, output, message.isBlank() ? "AutoDL edit success." : message);
  }

  private String postMultipart(EditTask task) throws IOException, InterruptedException {
    String boundary = "FlowStudioBoundary" + UUID.randomUUID().toString().replace("-", "");
    byte[] body = multipartBody(boundary, task);
    System.out.println("[FlowStudio] AutoDL multipart bytes: " + body.length);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(normalizeBaseUrl() + "/edit"))
            .version(HttpClient.Version.HTTP_1_1)
            .timeout(Duration.ofHours(2))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    System.out.println("[FlowStudio] AutoDL response status: " + response.statusCode());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("AutoDL HTTP " + response.statusCode() + ": " + response.body());
    }
    return response.body();
  }

  private byte[] multipartBody(String boundary, EditTask task) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    writeField(output, boundary, "taskId", task.getTaskId());
    writeField(output, boundary, "sourcePrompt", nullToEmpty(task.getSourcePrompt()));
    writeField(output, boundary, "targetPrompt", task.getTargetPrompt());
    writeField(output, boundary, "targetWord", task.getTargetWord());
    writeFile(output, boundary, "video", task.getInputVideoPath(), "video/mp4");
    writeFile(output, boundary, "mask", task.getMaskPath(), "image/png");
    output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return output.toByteArray();
  }

  private void writeField(ByteArrayOutputStream output, String boundary, String name, String value)
      throws IOException {
    output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    output.write(nullToEmpty(value).getBytes(StandardCharsets.UTF_8));
    output.write("\r\n".getBytes(StandardCharsets.UTF_8));
  }

  private void writeFile(ByteArrayOutputStream output, String boundary, String name, Path path, String contentType)
      throws IOException {
    output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    output.write(
        ("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + path.getFileName() + "\"\r\n")
            .getBytes(StandardCharsets.UTF_8));
    output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    output.write(Files.readAllBytes(path));
    output.write("\r\n".getBytes(StandardCharsets.UTF_8));
  }

  private void copyResult(String resultPath, Path output) throws IOException {
    if (resultPath.startsWith("/")) {
      resultPath = normalizeBaseUrl() + resultPath;
    }

    if (resultPath.startsWith("http://") || resultPath.startsWith("https://")) {
      byte[] bytes =
          restClient
              .get()
              .uri(URI.create(resultPath))
              .retrieve()
              .body(byte[].class);
      if (bytes == null || bytes.length == 0) {
        throw new IOException("AutoDL result download returned empty content.");
      }
      Files.write(output, bytes);
      return;
    }

    Path source = Path.of(resultPath);
    if (!Files.exists(source)) {
      throw new IOException("AutoDL result path is not accessible from Java backend: " + resultPath);
    }
    Files.copy(source, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }

  private String normalizeBaseUrl() {
    String baseUrl = properties.getAutodlBaseUrl().trim();
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
