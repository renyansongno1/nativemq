package org.cloud.mq.control.panel.pojo;

/**
 * broker status enum
 */
public enum BrokerStatusTO {

    ACTIVE("ACTIVE"),
    DATA_MIGRATION("DATA_MIGRATION"),
    DOWN("DOWN");

    private final String graphqlName;

    private BrokerStatusTO(String graphqlName) {
        this.graphqlName = graphqlName;
    }

    @Override
    public String toString() {
        return this.graphqlName;
    }

}
