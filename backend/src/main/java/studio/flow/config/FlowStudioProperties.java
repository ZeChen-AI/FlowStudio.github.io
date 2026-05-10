package studio.flow.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowstudio")
public class FlowStudioProperties {
  private Path runtimeDir = Path.of("runtime");
  private String autodlBaseUrl = "";
  private boolean mockRunner = true;
  private long runnerTimeoutSeconds = 1800;

  public Path getRuntimeDir() {
    return runtimeDir;
  }

  public void setRuntimeDir(Path runtimeDir) {
    this.runtimeDir = runtimeDir;
  }

  public String getAutodlBaseUrl() {
    return autodlBaseUrl;
  }

  public void setAutodlBaseUrl(String autodlBaseUrl) {
    this.autodlBaseUrl = autodlBaseUrl;
  }

  public boolean isMockRunner() {
    return mockRunner;
  }

  public void setMockRunner(boolean mockRunner) {
    this.mockRunner = mockRunner;
  }

  public long getRunnerTimeoutSeconds() {
    return runnerTimeoutSeconds;
  }

  public void setRunnerTimeoutSeconds(long runnerTimeoutSeconds) {
    this.runnerTimeoutSeconds = runnerTimeoutSeconds;
  }
}
