package studio.flow.runner;

import java.nio.file.Path;

public record RunnerResult(boolean success, Path resultPath, String message) {}
