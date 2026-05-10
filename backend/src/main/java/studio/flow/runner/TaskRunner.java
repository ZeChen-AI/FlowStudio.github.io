package studio.flow.runner;

import studio.flow.model.EditTask;

public interface TaskRunner {
  RunnerResult run(EditTask task) throws Exception;
}
