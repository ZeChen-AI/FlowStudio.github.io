package studio.flow.runner;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import studio.flow.config.FlowStudioProperties;
import studio.flow.model.EditTask;

@Component
@ConditionalOnProperty(prefix = "flowstudio", name = "mock-runner", havingValue = "false")
public class AutodlTaskRunner implements TaskRunner {
  private final FlowStudioProperties properties;
  private final RestClient restClient;

  public AutodlTaskRunner(FlowStudioProperties properties) {
    this.properties = properties;
    this.restClient = RestClient.builder().build();
  }

  @Override
  public RunnerResult run(EditTask task) throws Exception {
    if (properties.getAutodlBaseUrl() == null || properties.getAutodlBaseUrl().isBlank()) {
      throw new IllegalStateException("AUTODL_BASE_URL is required when mock runner is disabled.");
    }

    System.out.println("[FlowStudio] Calling AutoDL: " + normalizeBaseUrl() + "/edit");

    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("taskId", task.getTaskId());
    builder.part("sourcePrompt", nullToEmpty(task.getSourcePrompt()));
    builder.part("targetPrompt", task.getTargetPrompt());
    builder.part("targetWord", task.getTargetWord());
    builder.part("video", new FileSystemResource(task.getInputVideoPath())).contentType(MediaType.APPLICATION_OCTET_STREAM);
    builder.part("mask", new FileSystemResource(task.getMaskPath())).contentType(MediaType.IMAGE_PNG);
    MultiValueMap<String, org.springframework.http.HttpEntity<?>> body = builder.build();

    Map<String, Object> response =
        restClient
            .post()
            .uri(normalizeBaseUrl() + "/edit")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

    if (response == null) {
      return new RunnerResult(false, null, "AutoDL returned an empty response.");
    }

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
