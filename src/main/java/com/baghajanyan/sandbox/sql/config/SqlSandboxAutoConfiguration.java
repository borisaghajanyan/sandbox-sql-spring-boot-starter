package com.baghajanyan.sandbox.sql.config;

import java.util.concurrent.Semaphore;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.baghajanyan.sandbox.core.fs.DeleteConfig;
import com.baghajanyan.sandbox.core.fs.TempFileManager;
import com.baghajanyan.sandbox.sql.docker.DockerProcessExecutor;
import com.baghajanyan.sandbox.sql.executor.SqlExecutor;

/**
 * Auto-configuration for the SQL sandbox environment.
 *
 * This class sets up the necessary beans for running SQL code in a sandboxed
 * environment, including beans for managing temporary files, controlling
 * concurrent executions, and configuring the Docker container.
 */
@AutoConfiguration
@EnableConfigurationProperties({ SqlSandboxProperties.class, SqlDeleteFileManagerProperties.class })
public class SqlSandboxAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    private TempFileManager sqlTempFileManager(SqlDeleteFileManagerProperties fileDeleteManagerProperties,
            SqlSandboxProperties sandboxProperties) {
        DeleteConfig deleteConfig = new DeleteConfig(fileDeleteManagerProperties.getMaxRetries(),
                fileDeleteManagerProperties.getRetryDelay(),
                fileDeleteManagerProperties.getTerminationTimeout());

        return new TempFileManager(deleteConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    private Semaphore sqlExecutionSemaphore(SqlSandboxProperties sandboxProperties) {
        return new Semaphore(sandboxProperties.getMaxConcurrency(), true);
    }

    @Bean
    @ConditionalOnMissingBean
    private DockerConfig sqlDockerConfig(SqlSandboxProperties sandboxProperties) {
        var security = sandboxProperties.getSecurity();
        return new DockerConfig(sandboxProperties.getMaxMemoryMb(), sandboxProperties.getMaxCpuUnits(),
                sandboxProperties.getMaxExecutionTime(), sandboxProperties.getDockerImage(),
                security.isEnableHardening(), security.isAllowNetwork(), security.isReadOnly(),
                security.getPidsLimit(), security.getRunAsUser(), security.getTmpfsSize(),
                security.isDropCapabilities(), security.isNoNewPrivileges());
    }

    @Bean
    @ConditionalOnMissingBean
    private DockerProcessExecutor sqlDockerProcess(DockerConfig dockerConfig) {
        return new DockerProcessExecutor(dockerConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    SqlExecutor sqlExecutor(Semaphore sqlExecutionSemaphore, TempFileManager sqlTempFileManager,
            DockerProcessExecutor sqlDockerProcess) {
        return new SqlExecutor(sqlExecutionSemaphore, sqlTempFileManager, sqlDockerProcess);
    }
}
