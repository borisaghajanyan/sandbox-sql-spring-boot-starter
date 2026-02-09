package com.baghajanyan.sandbox.sql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the SQL delete file manager.
 *
 * This class defines settings for managing the deletion of temporary files,
 * including retry mechanisms and timeout settings.
 */
@ConfigurationProperties(prefix = "sandboxcore.filemanager.delete")
public class SqlDeleteFileManagerProperties {

    /**
     * The maximum number of retries for deleting a file.
     */
    private int maxRetries = 5;

    /**
     * The delay between file deletion retries.
     */
    private Duration retryDelay = Duration.ofMillis(100);

    /**
     * The timeout for terminating the file deletion process.
     */
    private Duration terminationTimeout = Duration.ofMillis(500);

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public Duration getTerminationTimeout() {
        return terminationTimeout;
    }

    public void setTerminationTimeout(Duration terminationTimeout) {
        this.terminationTimeout = terminationTimeout;
    }
}