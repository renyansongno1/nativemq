package org.cloud.mq.control.panel.web;

import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.cloud.mq.control.panel.pojo.BrokerFilterTO;
import org.cloud.mq.control.panel.pojo.BrokerOnlineReqTO;
import org.cloud.mq.control.panel.pojo.BrokerPageTO;
import org.cloud.mq.control.panel.service.BrokerService;
import org.eclipse.microprofile.graphql.*;

/**
 * broker graphql resource
 * @author renyansong
 */
@GraphQLApi
@ApplicationScoped
public class BrokerResource {

    @Inject
    BrokerService brokerService;

    private final BroadcastProcessor<Boolean> processor = BroadcastProcessor.create();

    @RolesAllowed({"profile"})
    @Query("brokerPageQry")
    @Description("broker page query")
    public BrokerPageTO brokerPageQry(@Source BrokerFilterTO brokerFilter) {
        return brokerService.brokerPageQry(brokerFilter);
    }

    @RolesAllowed({"profile"})
    @Mutation("beOnlineBroker")
    @Description("broker online")
    public Boolean beOnlineBroker(BrokerOnlineReqTO brokerOnlineReq) {
        boolean res = brokerService.beOnlineBroker(brokerOnlineReq);
        processor.onNext(res);
        return res;
    }

}
