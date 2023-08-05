package org.cloud.mq.broker.conf;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * config bean
 * @author renyansong
 */
@ApplicationScoped
@Getter
@Setter
@ToString
public class BrokerConfig {

    /**
     * owning cluster
     */
    private String cluster = "default";

    /**
     * k8s domain
     */
    private String domain = "broker.mw";

}
