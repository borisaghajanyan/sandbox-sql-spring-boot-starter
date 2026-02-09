package com.baghajanyan.sandbox.sql.executor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baghajanyan.sandbox.core.executor.CodeExecutor;
import com.baghajanyan.sandbox.core.executor.ExecutionResult;
import com.baghajanyan.sandbox.core.fs.TempFileManager;
import com.baghajanyan.sandbox.core.model.CodeSnippet;
import com.baghajanyan.sandbox.sql.docker.DockerProcessExecutor;
import com.baghajanyan.sandbox.sql.docker.DockerProcessException.DockerProcessThreadException;
import com.baghajanyan.sandbox.sql.docker.DockerProcessException.DockerProcessTimeoutException;

/**
 * Executes a SQL code snippet in a sandboxed environment.
 *
 * This class implements the {@link CodeExecutor} interface and is responsible
 * for executing SQL code in a Docker container. It uses a {@link Semaphore} to
 * control concurrent executions and a {@link TempFileManager} to manage
 * temporary files.
 */
public class SqlExecutor implements CodeExecutor {

    private static final long EXECUTION_TIME_ZERO = 0;
    private static final int EXCEPTION_EXIT_CODE = -1;
    private static final Logger logger = LoggerFactory.getLogger(SqlExecutor.class);

    private final Semaphore semaphore;
    private final TempFileManager fileManager;
    private final DockerProcessExecutor process;

    public SqlExecutor(Semaphore semaphore, TempFileManager fileManager, DockerProcessExecutor process) {
        this.semaphore = semaphore;
        this.fileManager = fileManager;
        this.process = process;
    }

    /**
     * Executes the given SQL snippet.
     *
     * The snippet timeout is injected as a session-level statement timeout and
     * applies to all statements in the snippet.
     *
     * @param snippet the SQL code snippet to execute.
     * @return the result of the execution.
     */
    @Override
    public ExecutionResult execute(CodeSnippet snippet) {
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            return executeInDocker(snippet);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Execution interrupted while waiting for permit", e);
            return new ExecutionResult(EXCEPTION_EXIT_CODE, null, "Execution interrupted",
                    Duration.ofMillis(EXECUTION_TIME_ZERO));
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private ExecutionResult executeInDocker(CodeSnippet snippet) {
        Path dockerInputFile = null;

        try {
            var sqlWithTimeout = wrapWithStatementTimeout(snippet.code(), snippet.timeout());
            dockerInputFile = fileManager.createTempFile("sql-snippet-" + System.nanoTime(), ".sql");
            fileManager.write(dockerInputFile, sqlWithTimeout);

            var dockerProcess = process.execute(dockerInputFile);
            return parseDockerExecutionResult(dockerProcess, dockerInputFile);
        } catch (IOException e) {
            logger.error("Failed to create/write temp file for SQL snippet", e);
            return new ExecutionResult(EXCEPTION_EXIT_CODE, null, "Failed to create/write temp file: " + e.getMessage(),
                    Duration.ofMillis(EXECUTION_TIME_ZERO));
        } catch (DockerProcessThreadException e) {
            logger.error("Docker process failed while executing SQL snippet", e);
            return new ExecutionResult(EXCEPTION_EXIT_CODE, null, "Failed to handle docker process: " + e.getMessage(),
                    Duration.ofMillis(EXECUTION_TIME_ZERO));
        } catch (DockerProcessTimeoutException e) {
            logger.warn("SQL snippet execution timed out", e);
            return new ExecutionResult(EXCEPTION_EXIT_CODE, null, "Snippet execution timed out: " + e.getMessage(),
                    Duration.ofMillis(EXECUTION_TIME_ZERO));
        } finally {
            if (dockerInputFile != null) {
                // Cleanup the temp SQL file asynchronously.
                fileManager.deleteAsync(dockerInputFile);
            }
        }
    }

    private ExecutionResult parseDockerExecutionResult(Process dockerProcess, Path dockerInputFile) {
        int exitCode = dockerProcess.exitValue();
        var stdout = new BufferedReader(new InputStreamReader(dockerProcess.getInputStream()));
        var stderr = new BufferedReader(new InputStreamReader(dockerProcess.getErrorStream()));

        // stdout carries CSV output plus the execution time marker.
        String out = stdout.lines().reduce("", (a, b) -> a + b + "\n").trim();
        // stderr contains psql errors and timing output from \\timing.
        String err = stderr.lines().reduce("", (a, b) -> a + b + "\n").trim();

        var timing = extractExecutionTime(out);
        if (timing != null) {
            out = timing.cleanedOutput;
        }

        if (exitCode == 0) {
            if (out.isEmpty()) {
                out = "[]";
            } else {
                out = CsvJsonConverter.toJson(out);
            }
        } else if (out.isEmpty() && err.isEmpty()) {
            var parentDir = dockerInputFile != null ? dockerInputFile.getParent() : null;
            var pathHint = parentDir != null ? (" Temp directory: " + parentDir) : "";
            err = "Docker execution failed with no output. Common causes include a missing Docker daemon or a temp "
                    + "directory that is not shared with Docker." + pathHint;
        }

        var duration = timing != null ? timing.duration : Duration.ZERO;
        return new ExecutionResult(exitCode, out, err, duration);
    }

    private String wrapWithStatementTimeout(String sql, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return sql;
        }
        long ms = timeout.toMillis();
        return "SET statement_timeout = " + ms + ";\n" + sql;
    }

    private TimingResult extractExecutionTime(String output) {
        if (output == null || output.isEmpty()) {
            return null;
        }
        var lines = output.split("\\R");
        var cleaned = new StringBuilder();
        Duration duration = null;
        for (var line : lines) {
            var trimmed = line.trim();
            if (trimmed.startsWith("__EXECUTION_TIME__")) {
                var parts = trimmed.split(":", 2);
                if (parts.length == 2) {
                    try {
                        long ms = Long.parseLong(parts[1].trim());
                        duration = Duration.ofMillis(ms);
                    } catch (NumberFormatException ignored) {
                    }
                }
                continue;
            }
            if (!line.isEmpty()) {
                cleaned.append(line).append('\n');
            }
        }
        var cleanedOut = cleaned.toString().trim();
        if (duration == null) {
            return null;
        }
        return new TimingResult(duration, cleanedOut);
    }

    private static final class TimingResult {
        private final Duration duration;
        private final String cleanedOutput;

        private TimingResult(Duration duration, String cleanedOutput) {
            this.duration = duration;
            this.cleanedOutput = cleanedOutput;
        }
    }

}
