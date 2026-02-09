package com.baghajanyan.sandbox.sql.executor;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import com.baghajanyan.sandbox.core.fs.DeleteConfig;
import com.baghajanyan.sandbox.core.fs.TempFileManager;
import com.baghajanyan.sandbox.core.model.CodeSnippet;
import com.baghajanyan.sandbox.sql.config.DockerConfig;
import com.baghajanyan.sandbox.sql.docker.DockerProcessExecutor;
import com.baghajanyan.sandbox.sql.docker.DockerProcessException.DockerProcessThreadException;
import com.baghajanyan.sandbox.sql.docker.DockerProcessException.DockerProcessTimeoutException;

@Tag("integration")
class SqlCodeExecutorTestIT {
        TempFileManager fileManager = Mockito.spy(
                        new TempFileManager(
                                        new DeleteConfig(1, Duration.ofMillis(100), Duration.ofMillis(100))));
        Semaphore semaphore = new Semaphore(10, true);

        @Test
        void execute() {
                var executor = new SqlExecutor(semaphore, fileManager, dockerProcess("postgres:16"));

                var sqlSnippet = """
                                CREATE TEMP TABLE temp_values (
                                  id INT,
                                  label TEXT
                                );
                                INSERT INTO temp_values (id, label) VALUES
                                  (1, 'value 1'),
                                  (2, 'value 2');
                                SELECT id, label
                                FROM temp_values
                                ORDER BY id;
                                DROP TABLE temp_values;
                                """;
                var snippet = new CodeSnippet(sqlSnippet, Duration.ofSeconds(1), "sql");
                var result = executor.execute(snippet);

                assertAll(
                                () -> assertEquals(
                                                "[{\"id\":\"1\",\"label\":\"value 1\"},{\"id\":\"2\",\"label\":\"value 2\"}]",
                                                result.stdout()),
                                () -> assertEquals("", result.stderr()),
                                () -> assertEquals(0, result.exitCode()),
                                () -> assertTrue(result.executionTime().compareTo(Duration.ofSeconds(10)) < 0));

                verify(fileManager).deleteAsync(any());
        }

        @Test
        void execute_whenStatementTimeoutExceeded_returnsTimeoutError() {
                var executor = new SqlExecutor(semaphore, fileManager, dockerProcess("postgres:16"));

                var sqlSnippet = """
                                SELECT pg_sleep(0.01);
                                """;
                var snippet = new CodeSnippet(sqlSnippet, Duration.ofMillis(1), "sql");
                var result = executor.execute(snippet);

                assertAll(
                                () -> assertEquals("", result.stdout()),
                                () -> assertTrue(result.stderr().contains("timeout")),
                                () -> assertThat(result.exitCode(), is(not(0))),
                                () -> assertTrue(result.executionTime().compareTo(Duration.ofSeconds(10)) < 0));

                verify(fileManager).deleteAsync(any());
        }

        @Test
        void execute_whenSelectReturnsNoRows_returnsEmptyOutput() {
                var executor = new SqlExecutor(semaphore, fileManager, dockerProcess("postgres:16"));

                var sqlSnippet = """
                                SELECT 1 WHERE false;
                                """;
                var snippet = new CodeSnippet(sqlSnippet, Duration.ofSeconds(1), "sql");
                var result = executor.execute(snippet);

                assertAll(
                                () -> assertEquals("[]", result.stdout()),
                                () -> assertEquals("", result.stderr()),
                                () -> assertEquals(0, result.exitCode()),
                                () -> assertTrue(result.executionTime().compareTo(Duration.ofSeconds(10)) < 0));

                verify(fileManager).deleteAsync(any());
        }

        @Test
        void execute_whenSqlIsInvalid_returnsError() {
                var executor = new SqlExecutor(semaphore, fileManager, dockerProcess("postgres:16"));

                var sqlSnippet = """
                                SELECT * FROM does_not_exist;
                                """;
                var snippet = new CodeSnippet(sqlSnippet, Duration.ofSeconds(1), "sql");
                var result = executor.execute(snippet);

                assertAll(
                                () -> assertEquals("", result.stdout()),
                                () -> assertTrue(result.stderr().contains("does not exist")),
                                () -> assertThat(result.exitCode(), is(not(0))),
                                () -> assertTrue(result.executionTime().compareTo(Duration.ofSeconds(10)) < 0));

                verify(fileManager).deleteAsync(any());
        }

        @Test
        void execute_whenFileCreationFails_returnFailedExecutionResult() throws Exception {
                var dockerProcess = dockerProcess("postgres:16");
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
                var dockerProcess = dockerProcess("postgres:16");
                var executor = new SqlExecutor(semaphore, fileManager, dockerProcess);
                var snippet = new CodeSnippet("", Duration.ofSeconds(2), "sql");

                doThrow(new IOException("Failed to write to temp file")).when(fileManager).write(any(), any());

                var result = executor.execute(snippet);

                assertAll(
                                () -> assertNull(result.stdout()),
                                () -> assertEquals("Failed to create/write temp file: Failed to write to temp file",
                                                result.stderr()),
                                () -> assertEquals(-1, result.exitCode()),
                                () -> assertEquals(Duration.ofMillis(0), result.executionTime()));
                verify(fileManager).deleteAsync(any());
        }

        @Test
        void execute_whenSnippetExecutionTimesOut_returnFailedExecutionResult() throws Exception {
                var dockerProcess = dockerProcess("postgres:16");
                var executor = new SqlExecutor(semaphore, fileManager, dockerProcess);
                var snippet = new CodeSnippet("", Duration.ofSeconds(2), "sql");

                doThrow(new DockerProcessTimeoutException("Execution timed out")).when(dockerProcess).execute(any());

                var result = executor.execute(snippet);

                assertAll(
                                () -> assertNull(result.stdout()),
                                () -> assertEquals("Snippet execution timed out: Execution timed out", result.stderr()),
                                () -> assertEquals(-1, result.exitCode()),
                                () -> assertEquals(Duration.ofMillis(0), result.executionTime()));
                verify(fileManager).deleteAsync(any());
        }

        @Test
        void execute_whenDockerTimeoutExceeded_returnsTimeoutError() {
                var timeoutDocker = new DockerProcessExecutor(new DockerConfig(
                                32,
                                0.125,
                                Duration.ofSeconds(5),
                                "postgres:16",
                                true,
                                false,
                                true,
                                64,
                                "65534:65534",
                                "64m",
                                true,
                                true));
                var executor = new SqlExecutor(semaphore, fileManager, timeoutDocker);

                var sqlSnippet = """
                                SELECT pg_sleep(2);
                                """;
                var snippet = new CodeSnippet(sqlSnippet, Duration.ofSeconds(5), "sql");
                var result = executor.execute(snippet);

                assertAll(
                                () -> assertNull(result.stdout()),
                                () -> assertTrue(result.stderr().contains("Snippet execution timed out")),
                                () -> assertEquals(-1, result.exitCode()),
                                () -> assertEquals(Duration.ofMillis(0), result.executionTime()));

                verify(fileManager).deleteAsync(any());
        }

        @Test
        void execute_whenSnippetExecutionThreadFails_returnFailedExecutionResult() throws Exception {
                var dockerProcess = dockerProcess("postgres:16");
                var executor = new SqlExecutor(semaphore, fileManager, dockerProcess);
                var snippet = new CodeSnippet("", Duration.ofSeconds(2), "sql");

                doThrow(new DockerProcessThreadException("Execution failed", new RuntimeException("Some error")))
                                .when(dockerProcess).execute(any());

                var result = executor.execute(snippet);

                assertAll(
                                () -> assertNull(result.stdout()),
                                () -> assertEquals("Failed to handle docker process: Execution failed",
                                                result.stderr()),
                                () -> assertEquals(-1, result.exitCode()),
                                () -> assertEquals(Duration.ofMillis(0), result.executionTime()));
                verify(fileManager).deleteAsync(any());
        }

        @Test
        void execute_whenAcquireInterrupted_doesNotReleaseSemaphorePermit() {
                Semaphore interruptingSemaphore = new Semaphore(0, true) {
                        @Override
                        public void acquire() throws InterruptedException {
                                throw new InterruptedException("interrupted");
                        }
                };
                var dockerProcess = dockerProcess("postgres:16");
                var executor = new SqlExecutor(interruptingSemaphore, fileManager, dockerProcess);
                var snippet = new CodeSnippet("", Duration.ofSeconds(2), "sql");

                var result = executor.execute(snippet);

                assertAll(
                                () -> assertNull(result.stdout()),
                                () -> assertEquals("Execution interrupted", result.stderr()),
                                () -> assertEquals(-1, result.exitCode()),
                                () -> assertEquals(Duration.ofMillis(0), result.executionTime()),
                                () -> assertEquals(0, interruptingSemaphore.availablePermits()));

                verify(fileManager, never()).deleteAsync(any());
                Thread.interrupted();
        }

        private DockerProcessExecutor dockerProcess(String image) {
                return Mockito.spy(new DockerProcessExecutor(dockerConfig(image)));
        }

        private DockerConfig dockerConfig(String image) {
                return new DockerConfig(
                                64,
                                0.5,
                                Duration.ofSeconds(15),
                                image,
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
