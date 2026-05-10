package studio.flow.runner;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import studio.flow.model.EditTask;

@Component
@ConditionalOnProperty(prefix = "flowstudio", name = "mock-runner", havingValue = "true", matchIfMissing = true)
public class MockTaskRunner implements TaskRunner {
  @Override
  public RunnerResult run(EditTask task) throws Exception {
    Thread.sleep(1200);
    Path output = task.getTaskDir().resolve("result.mp4");
    Files.copy(task.getInputVideoPath(), output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    return new RunnerResult(true, output, "Mock runner copied input video as result.");
  }
}
