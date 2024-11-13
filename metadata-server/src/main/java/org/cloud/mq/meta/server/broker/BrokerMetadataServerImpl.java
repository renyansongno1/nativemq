package org.cloud.mq.meta.server.broker;

import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.api.*;
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
import java.util.List;

/**
 * broker server
 * @author renyansong
 */
@GrpcService
@Slf4j
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

    @Inject
    BrokerMetadata brokerMetadata;

    @Override
    public Uni<BrokerRegisterReply> brokerRegister(BrokerRegisterRequest request) {
        log.info("receive broker register:{}", request);
        if (electState.getState() != RaftStateEnum.LEADER) {
            // re req for leader
            ManagedChannel channel = raftClient.getChannelById(electState.getLeaderId());
            return Uni.createFrom().item(raftClient.registryBroker(channel, request));
        }
        if (!electState.isReady()) {
            log.warn("raft component is not ready");
            BrokerRegisterReply reply = BrokerRegisterReply.newBuilder()
                    .setSuccess(false)
                    .build();
            return Uni.createFrom().item(reply);
        }
        MetadataDefinition metadataDefinition = MetadataDefinition.builder()
                .metadataTypeEnum(MetadataTypeEnum.BROKER)
                .metadataOperateEnum(MetadataOperateEnum.ADD)
                .dataJson(GSON.toJson(request))
                .build();
        String json = GSON.toJson(metadataDefinition);
        log.info("meta definition is:{}", json);
        byte[] definitionBytes = json.getBytes(StandardCharsets.UTF_8);
        log.info("broker register data:{}", definitionBytes);
        ByteString bytes = ByteString.copyFrom(definitionBytes);
        if (log.isDebugEnabled()) {
            log.debug("leader append log data:{}", bytes);
        }
        AppendLogRes appendLogRes = raftComponent.appendEntry(AppendLogReq.newBuilder()
                        .setLogData(bytes)
                        .build());
        if (appendLogRes.getResult() == AppendLogRes.AppendResult.SUCCESS) {
            // wait other follower verify
            boolean success = peerWaterMark.waitQuorum(appendLogRes.getNextIndex());
            if (!success) {
                success = peerWaterMark.waitQuorum(appendLogRes.getNextIndex());
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
        // update Metadata
        brokerMetadata.putBroker(metadataDefinition);
        BrokerRegisterReply reply = BrokerRegisterReply.newBuilder()
                .setSuccess(true)
                .build();
        return Uni.createFrom().item(reply);
    }

    @Override
    public Uni<FindAllBrokerReply> findAllBroker(FindAllBrokerRequest request) {
        List<Broker> brokers = brokerMetadata.findBrokerListByCluster(request.getCluster());
        FindAllBrokerReply findAllBrokerReply = FindAllBrokerReply.newBuilder()
                .addAllBrokerList(brokers)
                .build();
        return Uni.createFrom().item(findAllBrokerReply);
    }

}
