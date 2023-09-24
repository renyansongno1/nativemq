package org.cloud.mq.meta.server.common;

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
