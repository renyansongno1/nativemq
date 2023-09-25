package org.cloud.mq.meta.server.broker;

/**
 * broker Metadata exception
 * @author renyansong
 */
public class BrokerMetadataException extends RuntimeException {

    public BrokerMetadataException(String msg) {
        super(msg);
    }

    public BrokerMetadataException(String msg, Throwable e) {
        super(msg, e);
    }

}
