package org.cloud.mq.meta.server.common;

/**
 * Metadata constant
 * @author renyansong
 */
public class MetadataConstant {

    /**
     * when reload Metadata file or some transaction commit, there will receive one message for metadata
     */
    public static final String METADATA_CHANGE_TOPIC = "metadata_change";

}
