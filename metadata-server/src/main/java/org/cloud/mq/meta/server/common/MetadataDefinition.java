package org.cloud.mq.meta.server.common;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * definition metadata
 * @author renyansong
 */
@Getter
@Setter
@ToString
@Builder
@RegisterForReflection
public class MetadataDefinition {

    /**
     * define Metadata type
     */
    private MetadataTypeEnum metadataTypeEnum;

    /**
     * define Metadata operate
     */
    private MetadataOperateEnum metadataOperateEnum;

    /**
     * json data
     */
    private String dataJson;

}
