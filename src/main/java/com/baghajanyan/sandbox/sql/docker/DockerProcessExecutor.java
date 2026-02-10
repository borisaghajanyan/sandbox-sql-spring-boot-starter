package com.baghajanyan.sandbox.sql.docker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baghajanyan.sandbox.sql.config.DockerConfig;
import com.baghajanyan.sandbox.sql.docker.DockerProcessException.DockerProcessThreadException;
import com.baghajanyan.sandbox.sql.docker.DockerProcessException.DockerProcessTimeoutException;

/**
 * Executes a script from a file in a sandboxed Docker container.
 *
 * This class is responsible for creating and running a Docker process with
 * specified resource limits and execution timeouts. It uses a
 * {@link DockerConfig} object to configure the container.
 */
public class DockerProcessExecutor {
    private static final Logger logger = LoggerFactory.getLogger(DockerProcessExecutor.class);
    private final DockerConfig dockerConfig;

    public DockerProcessExecutor(DockerConfig dockerConfig) {
        this.dockerConfig = dockerConfig;
    }

    /**
     * Executes the script from a temporary file in a Docker container.
     *
     * This method launches a short-lived container, applies security limits,
     * executes the SQL file, and returns the completed process.
     *
     * @param tmpFile the temporary file containing the script to execute.
     * @return the completed {@link Process} object.
     * @throws DockerProcessThreadException  if the Docker process fails to start or
     *                                       is interrupted.
     * @throws DockerProcessTimeoutException if the execution times out.
     */
    public Process execute(Path tmpFile) throws DockerProcessThreadException, DockerProcessTimeoutException {
        try {
            var builder = create(tmpFile);
            var process = builder.start();
            boolean finished = process.waitFor(dockerConfig.executionTimeout().toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                logger.warn("Docker process timed out after {} seconds",
                        dockerConfig.executionTimeout().toSeconds());
                throw new DockerProcessTimeoutException(
                        "Execution timed out after " + dockerConfig.executionTimeout().toSeconds() + " seconds");
            }
            return process;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("Failed to execute Docker process", e);
            throw new DockerProcessThreadException("Failed to execute Docker process", e);
        }
    }

    ProcessBuilder create(Path tmpFile) {
        List<String> command = new ArrayList<>();
        // Docker CLI invocation.
        command.add("docker");
        command.add("run");
        // Always remove the container.
        command.add("--rm");

        if (dockerConfig.securityHardening()) {
            if (!dockerConfig.allowNetwork()) {
                // Disallow outbound networking for a tighter sandbox.
                command.add("--network=none");
            }
            if (dockerConfig.readOnly()) {
                // Read-only root filesystem.
                command.add("--read-only");
                // Writable tmpfs for temp files.
                command.add("--tmpfs");
                // Mount /tmp as an in-memory filesystem (tmpfs) with read/write access,
                // disable execution and SUID for security, and limit its size.
                command.add("/tmp:rw,noexec,nosuid,size=" + dockerConfig.tmpfsSize());
            }
            if (dockerConfig.pidsLimit() > 0) {
                // Limit the number of processes inside the container.
                command.add("--pids-limit=" + dockerConfig.pidsLimit());
            }
            if (dockerConfig.dropCapabilities()) {
                // Drop all Linux capabilities to reduce attack surface.
                command.add("--cap-drop=ALL");
            }
            if (dockerConfig.noNewPrivileges()) {
                // Prevent privilege escalation within the container.
                command.add("--security-opt");
                command.add("no-new-privileges");
            }
        }
        if (!dockerConfig.runAsUser().isBlank()) {
            // Run container as a non-root user if configured for better security.
            command.add("--user");
            command.add(dockerConfig.runAsUser());
        }

        // Memory & CPU limits.
        command.add("-m");
        command.add(dockerConfig.maxMemoryMb() + "m");
        command.add("--cpus=" + dockerConfig.maxCpuUnits());

        var volumeSuffix = dockerConfig.securityHardening() && dockerConfig.readOnly() ? ":ro" : "";
        // Mount the SQL file to a fixed path in the container.
        command.add("-v");
        command.add(tmpFile.getParent() + ":/code" + volumeSuffix);

        // Pass the SQL file path to the container script via environment variable.
        command.add("-e");
        command.add("SQL_FILE=/code/" + tmpFile.getFileName());

        command.add("-e");
        command.add("POSTGRES_HOST_AUTH_METHOD=trust");

        command.add("--entrypoint");
        command.add("");

        // Use a writable working directory for Postgres temp files.
        command.add("-w");
        command.add("/tmp");

        command.add(dockerConfig.dockerImage());
        // Use a shell to run a small script that initializes and runs Postgres.
        command.add("/bin/bash");
        command.add("-c");
        var script = loadScriptTemplate();
        command.add(script);

        logger.info("SQL docker command: {}", command);
        return new ProcessBuilder(command);
    }

    private String loadScriptTemplate() {
        var resourcePath = "/sql/run-postgres.sh";
        try (InputStream stream = DockerProcessExecutor.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing script template: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load script template: " + resourcePath, e);
        }
    }
}
