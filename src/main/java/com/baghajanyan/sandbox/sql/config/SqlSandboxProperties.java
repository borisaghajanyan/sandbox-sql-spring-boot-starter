package com.baghajanyan.sandbox.sql.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the SQL sandbox.
 *
 * This class defines settings for the sandboxed execution of SQL code,
 * including concurrency limits, resource allocation (memory and CPU), and
 * execution timeouts.
 */
@ConfigurationProperties(prefix = "sandboxcore.sql")
public class SqlSandboxProperties {

    /**
     * The maximum number of concurrent SQL executions.
     */
    private int maxConcurrency = 5;

    /**
     * The maximum memory in megabytes allocated to the SQL container.
     */
    private int maxMemoryMb = 128;

    /**
     * The maximum CPU units allocated to the SQL container.
     */
    private double maxCpuUnits = 0.125;

    /**
     * The maximum time allowed for a single SQL execution.
     */
    private Duration maxExecutionTime = Duration.ofSeconds(15);

    /**
     * The Docker image to use for the SQL sandbox.
     */
    private String dockerImage = "postgres:16";

    /**
     * Security-related settings for the Docker sandbox.
     */
    private Security security = new Security();

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public Duration getMaxExecutionTime() {
        return maxExecutionTime;
    }

    public void setMaxExecutionTime(Duration maxExecutionTime) {
        this.maxExecutionTime = maxExecutionTime;
    }

    public int getMaxMemoryMb() {
        return maxMemoryMb;
    }

    public void setMaxMemoryMb(int maxMemoryMb) {
        this.maxMemoryMb = maxMemoryMb;
    }

    public double getMaxCpuUnits() {
        return maxCpuUnits;
    }

    public void setMaxCpuUnits(double maxCpuUnits) {
        this.maxCpuUnits = maxCpuUnits;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public void setDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public static class Security {
        /**
         * Enable hardened sandbox flags by default.
         */
        private boolean enableHardening = true;

        /**
         * Allow outbound/inbound network access.
         */
        private boolean allowNetwork = false;

        /**
         * Run the container with a read-only filesystem.
         */
        private boolean readOnly = true;

        /**
         * Limit the maximum number of processes inside the container.
         */
        private int pidsLimit = 64;

        /**
         * User and group to run as inside the container.
         */
        private String runAsUser = "65534:65534";

        /**
         * tmpfs size for /tmp (e.g., "64m").
         */
        private String tmpfsSize = "64m";

        /**
         * Drop all Linux capabilities.
         */
        private boolean dropCapabilities = true;

        /**
         * Prevent privilege escalation.
         */
        private boolean noNewPrivileges = true;

        public boolean isEnableHardening() {
            return enableHardening;
        }

        public void setEnableHardening(boolean enableHardening) {
            this.enableHardening = enableHardening;
        }

        public boolean isAllowNetwork() {
            return allowNetwork;
        }

        public void setAllowNetwork(boolean allowNetwork) {
            this.allowNetwork = allowNetwork;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        public void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }

        public int getPidsLimit() {
            return pidsLimit;
        }

        public void setPidsLimit(int pidsLimit) {
            this.pidsLimit = pidsLimit;
        }

        public String getRunAsUser() {
            return runAsUser;
        }

        public void setRunAsUser(String runAsUser) {
            this.runAsUser = runAsUser;
        }

        public String getTmpfsSize() {
            return tmpfsSize;
        }

        public void setTmpfsSize(String tmpfsSize) {
            this.tmpfsSize = tmpfsSize;
        }

        public boolean isDropCapabilities() {
            return dropCapabilities;
        }

        public void setDropCapabilities(boolean dropCapabilities) {
            this.dropCapabilities = dropCapabilities;
        }

        public boolean isNoNewPrivileges() {
            return noNewPrivileges;
        }

        public void setNoNewPrivileges(boolean noNewPrivileges) {
            this.noNewPrivileges = noNewPrivileges;
        }
    }
}
