package org.cloud.mq.meta.server.common;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * metadata operate enum
 * @author renyansong
 */
@RegisterForReflection
public enum MetadataOperateEnum {

    ADD,
    UPDATE,
    DELETE,

}
