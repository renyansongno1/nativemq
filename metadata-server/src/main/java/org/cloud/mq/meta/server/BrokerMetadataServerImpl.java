package org.cloud.mq.meta.server;

import io.grpc.ManagedChannel;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.cloud.mq.meta.api.BrokerRegisterReply;
import org.cloud.mq.meta.api.BrokerRegisterRequest;
import org.cloud.mq.meta.api.MetaBrokerService;
import org.cloud.mq.meta.server.raft.client.RaftClient;
import org.cloud.mq.meta.server.raft.election.ElectState;
import org.cloud.mq.meta.server.raft.election.RaftComponent;
import org.cloud.mq.meta.server.raft.election.RaftStateEnum;

/**
 * broker server
 * @author renyansong
 */
@GrpcService
public class BrokerMetadataServerImpl implements MetaBrokerService {

    @Inject
    RaftComponent raftComponent;

    @Inject
    ElectState electState;

    @Inject
    RaftClient raftClient;

    @Override
    public Uni<BrokerRegisterReply> brokerRegister(BrokerRegisterRequest request) {
        if (electState.getState() != RaftStateEnum.LEADER) {
            // re req for leader
            ManagedChannel channel = raftClient.getChannelById(electState.getLeaderId());
            return Uni.createFrom().item(raftClient.registryBroker(channel, request));
        }
        // raftComponent.appendEntry()
        BrokerRegisterReply reply = BrokerRegisterReply.newBuilder()
                .setSuccess(true)
                .build();
        return Uni.createFrom().item(reply);
    }

}
