package org.cloud.mq.meta.client;

import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * broker grpc client
 * @author renyansong
 */
@ApplicationScoped
public class MetaBrokerClient {

    @GrpcClient("metaBrokerService")
    org.cloud.mq.meta.api.MetaBrokerServiceGrpc.MetaBrokerServiceBlockingStub metaBrokerServiceBlockingStub;

    /**
     * broker register
     *
     * @param brokerRegisterRequest req
     * @return success
     */
    public Boolean registerBroker(org.cloud.mq.meta.api.BrokerRegisterRequest brokerRegisterRequest) {
        return metaBrokerServiceBlockingStub.brokerRegister(brokerRegisterRequest).getSuccess();
    }

}
