package org.cloud.mq.meta.server;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import org.cloud.mq.meta.api.BrokerRegisterReply;
import org.cloud.mq.meta.api.BrokerRegisterRequest;
import org.cloud.mq.meta.api.MetaBrokerService;

/**
 * broker server
 * @author renyansong
 */
@GrpcService
public class BrokerMetadataServerImpl implements MetaBrokerService {

    @Override
    public Uni<BrokerRegisterReply> brokerRegister(BrokerRegisterRequest request) {
        BrokerRegisterReply reply = BrokerRegisterReply.newBuilder()
                .setSuccess(true)
                .build();
        return Uni.createFrom().item(reply);
    }

}
