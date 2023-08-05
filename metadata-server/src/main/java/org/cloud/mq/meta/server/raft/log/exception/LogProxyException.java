package org.cloud.mq.meta.server.raft.log.exception;

/**
 * log proxy exception
 * @author renyansong
 */
public class LogProxyException extends RuntimeException{

    public LogProxyException(String message, Throwable cause) {
        super(message, cause);
    }

    public LogProxyException(String message) {
        super(message);
    }
}
