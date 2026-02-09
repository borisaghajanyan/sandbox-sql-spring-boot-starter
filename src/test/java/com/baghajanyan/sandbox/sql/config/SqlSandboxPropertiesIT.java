package com.baghajanyan.sandbox.sql.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = { SqlSandboxAutoConfiguration.class })
@ActiveProfiles("test")
class SqlSandboxPropertiesIT {

    @Autowired
    private SqlSandboxProperties sqlSandboxProperties;

    @Autowired
    private SqlDeleteFileManagerProperties sqlDeleteFileManagerProperties;

    @Test
    void sqlSandboxPropertiesAreLoadedCorrectly() {
        assertNotNull(sqlSandboxProperties);
        assertEquals(10, sqlSandboxProperties.getMaxConcurrency());
        assertEquals(32, sqlSandboxProperties.getMaxMemoryMb());
        assertEquals(0.5, sqlSandboxProperties.getMaxCpuUnits());
        assertEquals(Duration.ofSeconds(20), sqlSandboxProperties.getMaxExecutionTime());
        assertEquals("postgres:16-test", sqlSandboxProperties.getDockerImage());
        assertEquals(true, sqlSandboxProperties.getSecurity().isEnableHardening());
        assertEquals(false, sqlSandboxProperties.getSecurity().isAllowNetwork());
        assertEquals(true, sqlSandboxProperties.getSecurity().isReadOnly());
        assertEquals(64, sqlSandboxProperties.getSecurity().getPidsLimit());
        assertEquals("65534:65534", sqlSandboxProperties.getSecurity().getRunAsUser());
        assertEquals("64m", sqlSandboxProperties.getSecurity().getTmpfsSize());
        assertEquals(true, sqlSandboxProperties.getSecurity().isDropCapabilities());
        assertEquals(true, sqlSandboxProperties.getSecurity().isNoNewPrivileges());
    }

    @Test
    void sqlDeleteFileManagerPropertiesAreLoadedCorrectly() {
        assertNotNull(sqlDeleteFileManagerProperties);
        assertEquals(3, sqlDeleteFileManagerProperties.getMaxRetries());
        assertEquals(Duration.ofMillis(50), sqlDeleteFileManagerProperties.getRetryDelay());
        assertEquals(Duration.ofMillis(200), sqlDeleteFileManagerProperties.getTerminationTimeout());
    }
}
