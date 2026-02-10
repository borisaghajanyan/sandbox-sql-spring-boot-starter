package com.baghajanyan.sandbox.sql.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.baghajanyan.sandbox.sql.config.DockerConfig;
import com.baghajanyan.sandbox.sql.docker.DockerProcessException.DockerProcessThreadException;
import com.baghajanyan.sandbox.sql.docker.DockerProcessException.DockerProcessTimeoutException;

class DockerProcessExecutorTest {

    @Test
    void create_buildsHardenedCommand() {
        var executor = new DockerProcessExecutor(defaultConfig());
        var builder = executor.create(Path.of("/tmp/sql-snippet.sql"));
        var command = builder.command();

        assertTrue(command.contains("--rm"));
        int entrypointIndex = command.indexOf("--entrypoint");
        assertTrue(entrypointIndex >= 0);
        assertEquals("", command.get(entrypointIndex + 1));
        assertTrue(command.contains("/bin/bash"));
        assertTrue(command.contains("-c"));
        assertTrue(command.contains("--network=none"));
        assertTrue(command.contains("--read-only"));
        assertTrue(command.contains("--cap-drop=ALL"));
        assertTrue(command.contains("--security-opt"));
        assertTrue(command.contains("no-new-privileges"));
        assertTrue(command.contains("--user"));
        assertTrue(command.contains("65534:65534"));
        assertTrue(command.contains("-m"));
        assertTrue(command.contains("128m"));
        assertTrue(command.stream().anyMatch(arg -> arg.startsWith("--cpus=")));
        assertTrue(command.contains("-e"));
        assertTrue(command.stream().anyMatch(arg -> arg.equals("SQL_FILE=/code/sql-snippet.sql")));
        assertTrue(command.contains("-v"));
        assertTrue(command.stream().anyMatch(arg -> arg.startsWith("/tmp:/code")));
    }

    @Test
    void create_buildsRelaxedCommand() {
        var relaxed = new DockerConfig(
                128,
                0.125,
                Duration.ofSeconds(10),
                "postgres:16",
                false,
                true,
                false,
                0,
                "65534:65534",
                "64m",
                false,
                false);
        var executor = new DockerProcessExecutor(relaxed);
        var builder = executor.create(Path.of("/tmp/sql-snippet.sql"));
        var command = builder.command();

        assertTrue(command.contains("--rm"));
        int entrypointIndex = command.indexOf("--entrypoint");
        assertTrue(entrypointIndex >= 0);
        assertEquals("", command.get(entrypointIndex + 1));
        assertTrue(command.contains("/bin/bash"));
        assertTrue(command.contains("-c"));
        assertTrue(command.contains("-m"));
        assertTrue(command.contains("128m"));
        assertTrue(command.stream().anyMatch(arg -> arg.startsWith("--cpus=")));
        assertTrue(command.contains("postgres:16"));
        assertTrue(command.contains("-v"));
        assertTrue(command.stream().anyMatch(arg -> arg.startsWith("/tmp:/code")));
        assertTrue(command.contains("-e"));
        assertTrue(command.stream().anyMatch(arg -> arg.equals("SQL_FILE=/code/sql-snippet.sql")));
        assertTrue(command.contains("--user"));
        assertTrue(command.contains("65534:65534"));
    }

    @Test
    void execute_whenProcessTimesOut_throwsTimeoutException() throws Exception {
        var executor = Mockito.spy(new DockerProcessExecutor(defaultConfig()));
        var builder = Mockito.mock(ProcessBuilder.class);
        var process = Mockito.mock(Process.class);
        Mockito.doReturn(builder).when(executor).create(Mockito.any());
        Mockito.when(builder.start()).thenReturn(process);
        Mockito.when(process.waitFor(Mockito.anyLong(), Mockito.any())).thenReturn(false);

        assertThrows(DockerProcessTimeoutException.class, () -> executor.execute(Path.of("/tmp/sql.sql")));
        Mockito.verify(process).destroyForcibly();
    }

    @Test
    void execute_whenProcessStartFails_throwsThreadException() throws Exception {
        var executor = Mockito.spy(new DockerProcessExecutor(defaultConfig()));
        var builder = Mockito.mock(ProcessBuilder.class);
        Mockito.doReturn(builder).when(executor).create(Mockito.any());
        Mockito.when(builder.start()).thenThrow(new IOException("boom"));

        assertThrows(DockerProcessThreadException.class, () -> executor.execute(Path.of("/tmp/sql.sql")));
    }

    private DockerConfig defaultConfig() {
        return new DockerConfig(
                128,
                0.125,
                Duration.ofSeconds(10),
                "postgres:16",
                true,
                false,
                true,
                64,
                "65534:65534",
                "64m",
                true,
                true);
    }
}
