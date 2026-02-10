package com.baghajanyan.sandbox.sql.executor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;

import com.baghajanyan.sandbox.core.fs.TempFileManager;
import com.baghajanyan.sandbox.core.model.CodeSnippet;
import com.baghajanyan.sandbox.sql.docker.DockerProcessExecutor;
import com.baghajanyan.sandbox.sql.docker.DockerProcessException.DockerProcessThreadException;

class SqlCodeExecutorTest {
        TempFileManager fileManager = mock(TempFileManager.class);
        Semaphore semaphore = mock(Semaphore.class);
        DockerProcessExecutor dockerProcess = mock(DockerProcessExecutor.class);

        @Test
        void execute_whenFileCreationFails_returnFailedExecutionResult() throws Exception {
                var executor = new SqlExecutor(semaphore, fileManager, dockerProcess);
                var snippet = new CodeSnippet("", Duration.ofSeconds(2), "sql");

                doThrow(new IOException("Disk full")).when(fileManager).createTempFile(any(), any());

                var result = executor.execute(snippet);

                assertAll(
                                () -> assertNull(result.stdout()),
                                () -> assertEquals("Failed to create/write temp file: Disk full", result.stderr()),
                                () -> assertEquals(-1, result.exitCode()),
                                () -> assertEquals(Duration.ofMillis(0), result.executionTime()));
                verify(fileManager, never()).deleteAsync(any());
        }

        @Test
        void execute_whenFileWriteFails_returnFailedExecutionResult() throws Exception {
                var executor = new SqlExecutor(semaphore, fileManager, dockerProcess);
                var snippet = new CodeSnippet("", Duration.ofSeconds(2), "sql");
                doReturn(Path.of("temp-file.sql")).when(fileManager).createTempFile(any(), any());

                doThrow(new IOException("Failed to write to temp file")).when(fileManager).write(any(), any());

                var result = executor.execute(snippet);

                assertAll(
                                () -> assertNull(result.stdout()),
                                () -> assertEquals("Failed to create/write temp file: Failed to write to temp file",
                                                result.stderr()),
                                () -> assertEquals(-1, result.exitCode()),
                                () -> assertEquals(Duration.ofMillis(0), result.executionTime()));

                verify(fileManager).createTempFile(any(), any());
                verify(fileManager).write(any(), any());
                verify(fileManager).deleteAsync(any());
        }

        @Test
        void execute_whenSnippetExecutionThreadFails_returnFailedExecutionResult() throws Exception {
                var executor = new SqlExecutor(semaphore, fileManager, dockerProcess);
                var snippet = new CodeSnippet("", Duration.ofSeconds(2), "sql");

                doThrow(new DockerProcessThreadException("Execution failed", new RuntimeException("Some error")))
                                .when(dockerProcess).execute(any());
                doReturn(Path.of("temp-file.sql")).when(fileManager).createTempFile(any(), any());

                var result = executor.execute(snippet);

                assertAll(
                                () -> assertNull(result.stdout()),
                                () -> assertEquals("Failed to handle docker process: Execution failed",
                                                result.stderr()),
                                () -> assertEquals(-1, result.exitCode()),
                                () -> assertEquals(Duration.ofMillis(0), result.executionTime()));

                verify(fileManager).createTempFile(any(), any());
                verify(fileManager).write(any(), any());
                verify(fileManager).deleteAsync(any());
        }
}
