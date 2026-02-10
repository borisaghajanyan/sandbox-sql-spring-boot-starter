# sandbox-sql-spring-boot-starter

Spring Boot starter for executing SQL code in a secure, isolated Docker sandbox, integrating [sandbox-core](https://github.com/borisaghajanyan/sandbox-core) library with auto-configuration and configurable concurrency.

## Requirements

- Java 21
- Docker (local daemon available to run `postgres:16`)

## Introduction

The `sandbox-sql-spring-boot-starter` provides a convenient way to integrate SQL code execution into your Spring Boot applications. It leverages Docker to create secure, isolated environments for running SQL scripts. This starter builds upon the [sandbox-core](https://github.com/borisaghajanyan/sandbox-core) library, offering seamless auto-configuration and easy customization of execution parameters.

## Features

- **Secure Sandboxing:** Runs SQL inside isolated Docker containers.
- **Resource Management:** Configurable limits for CPU and memory usage for each execution.
- **Concurrency Control:** Manages the number of simultaneous SQL executions to prevent system overload.
- **Execution Timeout:** Prevents long-running queries from consuming excessive resources.
- **Auto-configuration:** Seamless integration with Spring Boot's auto-configuration mechanism.
- **Temporary File Management:** Handles the creation and deletion of temporary SQL files.

## JitPack

You can also consume this starter via JitPack. Add the repository and dependency below.

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

```xml
<dependency>
    <groupId>com.github.borisaghajanyan</groupId>
    <artifactId>sandbox-sql-spring-boot-starter</artifactId>
    <version>{TAG}</version>
</dependency>
```

## Configuration

You can customize the behavior of the SQL sandbox using properties in your `application.properties` or `application.yml` file. The executor writes each snippet to a temporary file and runs it inside Docker, so the file deletion settings control cleanup of those temporary files after execution. If no properties are explicitly set, the default values listed below will be used.

| Property                                             | Description                                                                               | Default Value      |
| :--------------------------------------------------- | :---------------------------------------------------------------------------------------- | :----------------- |
| `sandboxcore.sql.max-concurrency`                    | Maximum number of concurrent SQL executions.                                              | `5`                |
| `sandboxcore.sql.max-memory-mb`                      | Maximum memory (in MB) allocated to the Docker container for each execution.              | `128`              |
| `sandboxcore.sql.max-cpu-units`                      | Maximum CPU units allocated to the Docker container (e.g., `0.125` for 12.5% of one CPU). | `0.125`            |
| `sandboxcore.sql.max-execution-time`                 | Maximum time allowed for a single SQL execution (e.g., `15s`).                            | `15s` (15 seconds) |
| `sandboxcore.sql.docker-image`                       | The Docker image to use for SQL execution.                                                | `postgres:16`      |
| `sandboxcore.sql.security.enable-hardening`          | Enable hardened Docker sandbox flags.                                                     | `true`             |
| `sandboxcore.sql.security.allow-network`             | Allow network access for the container.                                                   | `false`            |
| `sandboxcore.sql.security.read-only`                 | Run the container with a read-only filesystem.                                            | `true`             |
| `sandboxcore.sql.security.pids-limit`                | Max processes allowed inside the container.                                               | `64`               |
| `sandboxcore.sql.security.run-as-user`               | User/group to run as inside the container.                                                | `65534:65534`      |
| `sandboxcore.sql.security.tmpfs-size`                | Size of tmpfs mounted at `/tmp`.                                                          | `64m`              |
| `sandboxcore.sql.security.drop-capabilities`         | Drop all Linux capabilities.                                                              | `true`             |
| `sandboxcore.sql.security.no-new-privileges`         | Prevent privilege escalation inside the container.                                        | `true`             |
| `sandboxcore.filemanager.delete.max-retries`         | Maximum retries for deleting temporary files.                                             | `5`                |
| `sandboxcore.filemanager.delete.retry-delay`         | Delay between retry attempts for file deletion (e.g., `100ms`).                           | `100ms`            |
| `sandboxcore.filemanager.delete.termination-timeout` | Timeout for forcibly terminating file deletion (e.g., `500ms`).                           | `500ms`            |

Note: snippets are written to temporary files before execution in Docker, so these deletion settings control cleanup.

**Example `application.yml`:**

```yaml
sandboxcore:
  sql:
    max-concurrency: 10
    max-memory-mb: 64
    max-cpu-units: 0.5
    max-execution-time: 20s
    docker-image: postgres:16
    security:
      enable-hardening: true
      allow-network: false
      read-only: true
      pids-limit: 64
      run-as-user: "65534:65534"
      tmpfs-size: 64m
      drop-capabilities: true
      no-new-privileges: true
  filemanager:
    delete:
      max-retries: 3
      retry-delay: 50ms
      termination-timeout: 200ms
```

## Usage

Once configured, you can inject the `SqlExecutor` bean into your Spring components and use it to execute SQL. Each snippet is written to a temporary file and executed inside a Docker container. The output is emitted as CSV by `psql` and converted to JSON.

**Example SQL Snippet:**

```sql
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
```

**Integrating with `SqlExecutor`:**

```java
import com.baghajanyan.sandbox.core.executor.CodeExecutor;
import com.baghajanyan.sandbox.core.executor.ExecutionResult;
import com.baghajanyan.sandbox.core.model.CodeSnippet;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class SqlExecutionService {

    private final CodeExecutor sqlExecutor;

    public SqlExecutionService(CodeExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    public String executeSql(String sql) {
        CodeSnippet snippet = new CodeSnippet(sql, Duration.ofSeconds(2), "sql");
        ExecutionResult result = sqlExecutor.execute(snippet);

        // Analyze the execution result
        if (result.exitCode() == 0) {
            System.out.println("SQL Output: \n" + result.stdout());
            System.out.println("Execution Time: " + result.executionTime().toMillis() + " ms");
            return result.stdout();
        } else {
            var errorDetails = (result.stderr() == null || result.stderr().isBlank())
                    ? "No error output captured"
                    : result.stderr();
            System.err.println("SQL Error (Exit Code: " + result.exitCode() + "):\n" + errorDetails);
            System.err.println("Execution Time: " + result.executionTime().toMillis() + " ms");
            return "Error (Exit Code: " + result.exitCode() + "):" + "\n" + result.stderr();
        }
    }
}
```

**Understanding `ExecutionResult`:**

The `execute` method returns an `ExecutionResult` object, which provides the following information:

- `exitCode()`: The exit status of the SQL process. A value of `0` typically indicates successful execution.
- `stdout()`: The standard output generated by the SQL execution (CSV converted to JSON when exit code is `0`).
- `stderr()`: The standard error output generated by the SQL execution. This may include `psql` timing output even on success.
- `executionTime()`: The time taken for SQL execution inside the sandbox, extracted from `psql` timing output.

## Notes

- The snippet timeout is enforced via `SET statement_timeout` and applies to all statements in the snippet (DDL/DML and queries).
- The SQL file is written via `TempFileManager` (typically under the system temp directory). If Docker Desktop uses a non-default sharing configuration, ensure the temp directory is shared.
- If Docker cannot read the SQL file from the host, execution may fail with an empty output and a non-zero exit code. This usually means the temp directory is not shared with Docker.
- Resources are cleaned up after each run: the temp SQL file is deleted asynchronously, the Docker container runs with `--rm` so it is removed on exit, and the container script removes its temp files and the Postgres data directory.
- When `sandboxcore.sql.security.enable-hardening` is set to `false`, other security flags (`allow-network`, `read-only`, `pids-limit`, `drop-capabilities`, `no-new-privileges`) are ignored. The `run-as-user` setting is still applied.

## Troubleshooting

- Always inspect `stderr` on failures. It contains `psql` error output and may include Docker/runtime errors.
- A common cause of empty output with a non-zero exit code is a temp directory that is not shared with Docker Desktop. Share your system temp directory or configure Docker to allow it.
- Integration tests are tagged with `@Tag("integration")` and require Docker to be available. Run them only in environments with Docker installed and running.

## Security Notes

Containers are started with hardened flags (no network, read-only filesystem, dropped capabilities, no-new-privileges, PID limits, and non-root user). For the strongest isolation, consider running Docker in rootless mode and further tightening via seccomp/AppArmor profiles.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
