package org.cloud.mq.meta.server.broker;

import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.cloud.mq.meta.api.BrokerRegisterReply;
import org.cloud.mq.meta.api.BrokerRegisterRequest;
import org.cloud.mq.meta.api.MetaBrokerService;
import org.cloud.mq.meta.raft.AppendLogReq;
import org.cloud.mq.meta.raft.AppendLogRes;
import org.cloud.mq.meta.server.common.MetadataDefinition;
import org.cloud.mq.meta.server.common.MetadataOperateEnum;
import org.cloud.mq.meta.server.common.MetadataTypeEnum;
import org.cloud.mq.meta.server.raft.client.RaftClient;
import org.cloud.mq.meta.server.raft.election.ElectState;
import org.cloud.mq.meta.server.raft.election.RaftComponent;
import org.cloud.mq.meta.server.raft.election.RaftStateEnum;
import org.cloud.mq.meta.server.raft.peer.PeerWaterMark;

import java.nio.charset.StandardCharsets;

/**
 * broker server
 * @author renyansong
 */
@GrpcService
public class BrokerMetadataServerImpl implements MetaBrokerService {

    private static final Gson GSON = new Gson();

    @Inject
    RaftComponent raftComponent;

    @Inject
    ElectState electState;

    @Inject
    RaftClient raftClient;

    @Inject
    PeerWaterMark peerWaterMark;

    @Override
    public Uni<BrokerRegisterReply> brokerRegister(BrokerRegisterRequest request) {
        if (electState.getState() != RaftStateEnum.LEADER) {
            // re req for leader
            ManagedChannel channel = raftClient.getChannelById(electState.getLeaderId());
            return Uni.createFrom().item(raftClient.registryBroker(channel, request));
        }
        MetadataDefinition metadataDefinition = MetadataDefinition.builder()
                .metadataTypeEnum(MetadataTypeEnum.BROKER)
                .metadataOperateEnum(MetadataOperateEnum.ADD)
                .dataJson(GSON.toJson(request))
                .build();
        AppendLogRes appendLogRes = raftComponent.appendEntry(AppendLogReq.newBuilder()
                        .setLogData(ByteString.copyFrom(GSON.toJson(metadataDefinition).getBytes(StandardCharsets.UTF_8)))
                .build());
        if (appendLogRes.getResult() == AppendLogRes.AppendResult.SUCCESS) {
            // wait other follower verify
            boolean success = peerWaterMark.waitQuorum(appendLogRes.getNextIndex() - 1);
            if (!success) {
                success = peerWaterMark.waitQuorum(appendLogRes.getNextIndex() - 1);
            }
            if (!success) {
                BrokerRegisterReply reply = BrokerRegisterReply.newBuilder()
                        .setSuccess(false)
                        .build();
                return Uni.createFrom().item(reply);
            }
        } else {
            BrokerRegisterReply reply = BrokerRegisterReply.newBuilder()
                    .setSuccess(false)
                    .build();
            return Uni.createFrom().item(reply);
        }
        BrokerRegisterReply reply = BrokerRegisterReply.newBuilder()
                .setSuccess(true)
                .build();
        return Uni.createFrom().item(reply);
    }

}
