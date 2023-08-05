package org.cloud.mq.control.panel.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.cloud.mq.control.panel.pojo.BrokerFilterTO;
import org.cloud.mq.control.panel.pojo.BrokerOnlineReqTO;
import org.cloud.mq.control.panel.pojo.BrokerPageTO;
import org.cloud.mq.meta.client.MetaBrokerClient;

/**
 * broker core service
 * @author renyansong
 */
@ApplicationScoped
public class BrokerService {

    @Inject
    MetaBrokerClient metaBrokerClient;

    /**
     * query broker
     * @param brokerFilter condition
     * @return broker page res
     */
    public BrokerPageTO brokerPageQry(BrokerFilterTO brokerFilter) {
        return null;
    }

    /**
     * broker online
     * @param brokerOnlineReq req
     * @return result
     */
    public boolean beOnlineBroker(BrokerOnlineReqTO brokerOnlineReq) {

        return false;
    }
}
