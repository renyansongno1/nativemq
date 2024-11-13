package org.cloud.mq.meta.server.common;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Metadata op type
 * @author renyansong
 */
@RegisterForReflection
public enum MetadataTypeEnum {

    // Broker metadata
    BROKER,
    // Topic meatadata
    TOPIC

}
