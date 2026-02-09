package com.baghajanyan.sandbox.sql.docker;

/**
 * Represents an exception that occurs during the execution of a Docker process.
 * This class serves as a base for more specific Docker-related exceptions.
 */
public class DockerProcessException extends RuntimeException {
    /**
     * An exception indicating that a Docker process has timed out.
     */
    public static class DockerProcessTimeoutException extends DockerProcessException {
        public DockerProcessTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * An exception indicating that an error occurred in a Docker process thread.
     */
    public static class DockerProcessThreadException extends DockerProcessException {
        public DockerProcessThreadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public DockerProcessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DockerProcessException(String message) {
        super(message);
    }
}
