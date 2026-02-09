package com.baghajanyan.sandbox.sql.config;

import java.time.Duration;

/**
 * Represents the configuration for a Docker container used for sandboxed
 * execution.
 * This record holds settings such as memory limits, CPU allocation, execution
 * timeouts, and the Docker image to be used.
 *
 * @param maxMemoryMb       the maximum memory allocated to the container in
 *                          megabytes.
 * @param maxCpuUnits       the maximum CPU units allocated to the container.
 * @param executionTimeout  the maximum time allowed for code execution.
 * @param dockerImage       the name of the Docker image to be used for the
 *                          sandbox.
 * @param securityHardening whether to enable hardened sandbox flags.
 * @param allowNetwork      whether to allow network access.
 * @param readOnly          whether to run with a read-only filesystem.
 * @param pidsLimit         maximum number of processes in the container.
 * @param runAsUser         user/group to run as inside the container.
 * @param tmpfsSize         tmpfs size for /tmp (e.g., "64m").
 * @param dropCapabilities  whether to drop all Linux capabilities.
 * @param noNewPrivileges   whether to prevent privilege escalation.
 */
public record DockerConfig(
        int maxMemoryMb,
        double maxCpuUnits,
        Duration executionTimeout,
        String dockerImage,
        boolean securityHardening,
        boolean allowNetwork,
        boolean readOnly,
        int pidsLimit,
        String runAsUser,
        String tmpfsSize,
        boolean dropCapabilities,
        boolean noNewPrivileges) {
    public DockerConfig {
        if (maxMemoryMb <= 0) {
            throw new IllegalArgumentException("maxMemoryMb must be greater than 0");
        }
        if (maxCpuUnits <= 0) {
            throw new IllegalArgumentException("maxCpuUnits must be greater than 0");
        }
        if (executionTimeout == null || executionTimeout.isNegative() || executionTimeout.isZero()) {
            throw new IllegalArgumentException("executionTimeout must be a positive duration");
        }
        if (dockerImage == null) {
            throw new IllegalArgumentException("dockerImage must not be null");
        }
        if (pidsLimit < 0) {
            throw new IllegalArgumentException("pidsLimit must be >= 0");
        }
        if (runAsUser == null || runAsUser.isBlank()) {
            throw new IllegalArgumentException("runAsUser must not be blank");
        }
        if (tmpfsSize == null || tmpfsSize.isBlank()) {
            throw new IllegalArgumentException("tmpfsSize must not be blank");
        }
    }
}
