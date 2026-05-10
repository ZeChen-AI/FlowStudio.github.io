package studio.flow.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.flow.config.FlowStudioProperties;
import studio.flow.dto.HealthResponse;

@RestController
@RequestMapping("/api")
public class HealthController {
  private final FlowStudioProperties properties;

  public HealthController(FlowStudioProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/health")
  public HealthResponse health() {
    String baseUrl = properties.getAutodlBaseUrl() == null ? "" : properties.getAutodlBaseUrl();
    return new HealthResponse("ok", properties.isMockRunner(), baseUrl, !baseUrl.isBlank());
  }
}
