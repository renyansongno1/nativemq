package org.cloud.mq.meta.server.testing.raft;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.api.BrokerRegisterReply;
import org.cloud.mq.meta.api.BrokerRegisterRequest;
import org.cloud.mq.meta.api.MetaBrokerService;
import org.cloud.mq.meta.raft.AppendLogReq;
import org.cloud.mq.meta.raft.AppendLogRes;
import org.cloud.mq.meta.raft.RaftVoteRes;
import org.cloud.mq.meta.raft.ReadIndexRes;
import org.cloud.mq.meta.server.common.MetadataDefinition;
import org.cloud.mq.meta.server.common.MetadataTypeEnum;
import org.cloud.mq.meta.server.raft.client.RaftClient;
import org.cloud.mq.meta.server.raft.common.RaftUtils;
import org.cloud.mq.meta.server.raft.election.RaftComponent;
import org.cloud.mq.meta.server.raft.election.ElectState;
import org.cloud.mq.meta.server.raft.election.RaftStateEnum;
import org.cloud.mq.meta.server.raft.election.follower.RaftFollowerComponent;
import org.cloud.mq.meta.server.raft.election.heartbeat.HeartbeatComponent;
import org.cloud.mq.meta.server.raft.election.heartbeat.HeartbeatStreamObserver;
import org.cloud.mq.meta.server.raft.log.LogProxy;
import org.cloud.mq.meta.server.raft.peer.PeerWaterMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * raft test
 *
 * @author renyansong
 */
@QuarkusTest
@Slf4j
public class RaftCoreTest {

    private static final String PEER1 = "peer1";

    private static final String PEER2 = "peer2";

    private static final String PEER3 = "peer3";

    @InjectMock
    @Inject
    RaftClient raftClient;

    @Inject
    ElectState electState;

    @Inject
    HeartbeatStreamObserver heartbeatStreamObserver;

    @Inject
    HeartbeatComponent heartbeatComponent;

    @Inject
    RaftFollowerComponent followerComponent;

    @Inject
    RaftComponent raftComponent;

    @Inject
    PeerWaterMark peerWaterMark;

    @GrpcClient("metaBrokerService")
    org.cloud.mq.meta.api.MetaBrokerServiceGrpc.MetaBrokerServiceBlockingStub metaBrokerServiceBlockingStub;

    @Inject
    LogProxy logProxy;

    @BeforeEach
    void beforeRaftTest() {
        ManagedChannel peer2 = ManagedChannelBuilder.forAddress(PEER2, 8080)
                .usePlaintext()
                .build();
        ManagedChannel peer3 = ManagedChannelBuilder.forAddress(PEER3, 8080)
                .usePlaintext()
                .build();
        // channel mock
        List<ManagedChannel> managedChannels = Lists.newArrayList(peer2, peer3);
        Mockito.when(raftClient.getAllChannel()).thenReturn(managedChannels);

        Mockito.when(raftClient.getPeerAddrByChannel(peer2)).thenReturn(PEER2);
        Mockito.when(raftClient.getPeerAddrByChannel(peer3)).thenReturn(PEER3);

        Mockito.when(raftClient.getChannelById(1)).thenReturn(peer2);
        Mockito.when(raftClient.getChannelById(2)).thenReturn(peer3);
    }

    @AfterEach
    void clearState() throws Exception {
        electState.reset();
        MockitoAnnotations.openMocks(this);
        peerWaterMark.updateLowWaterMark(PEER1, 0);
        peerWaterMark.updateLowWaterMark(PEER2, 0);
        peerWaterMark.updateLowWaterMark(PEER3, 0);
        logProxy.clearAll();
    }

    /**
     * vote success test
     */
    @Test
    void voteSuccessTest() {
        // Mock res
        Mockito.when(raftClient.sendVote(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    Object[] arguments = invocation.getArguments();
                    if (arguments[0].toString().contains(PEER2)) {
                        return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.ACCEPT).build();
                    } else if (arguments[0].toString().contains(PEER3)) {
                        return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.REJECT).build();
                    } else {
                        return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.ACCEPT).build();
                    }
                });
        // send vote
        electState.becomeCandidate();
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(electState.getLeaderId()).isEqualTo(0)
        );
    }

    @Test
    void heartbeatSuccessTest() {
        // null impl msg sender
        StreamObserver<AppendLogReq> appendLogReqStreamObserver = new StreamObserver<>() {
            @Override
            public void onNext(AppendLogReq appendLogReq) {
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onCompleted() {
            }
        };
        Mockito.when(raftClient.appendLog(
                ArgumentMatchers.any(),
                ArgumentMatchers.any())).thenReturn(appendLogReqStreamObserver);
        // vote first
        voteSuccessTest();
        // heartbeat test
        AppendLogRes appendLogRes = AppendLogRes.newBuilder()
                .setResult(AppendLogRes.AppendResult.SUCCESS)
                .setMyId(1)
                .build();
        heartbeatComponent.heartbeat(AppendLogReq.newBuilder()
                .setLeaderId(RaftUtils.getIdByHost(null))
                .setLogIndex(0)
                .setTerm(electState.getTerm().get())
                .setLogData(ByteString.copyFrom(new byte[]{}))
                .build());
        heartbeatStreamObserver.onNext(appendLogRes);
        assertThat(electState.getLeaderId()).isEqualTo(0);
    }

    @Test
    void voteSuccessOnThirdTimesTest() {
        AtomicInteger times = new AtomicInteger();
        // Mock res
        Mockito.when(raftClient.sendVote(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    if (times.getAndIncrement() < 3) {
                        return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.REJECT).build();
                    } else {
                        return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.ACCEPT).build();
                    }
                });
        // send vote
        electState.becomeCandidate();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(electState.getLeaderId()).isEqualTo(0)
        );
    }

    @Test
    void voteExpireTest() {
        // Mock res
        Mockito.when(raftClient.sendVote(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    Object[] arguments = invocation.getArguments();
                    if (arguments[0].toString().contains(PEER2)) {
                        return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.ACCEPT).build();
                    } else if (arguments[0].toString().contains(PEER3)) {
                        return RaftVoteRes.newBuilder()
                                .setResult(RaftVoteRes.Result.TERM_EXPIRE)
                                .setLeaderId(2)
                                .setTerm(100)
                                .build();
                    } else {
                        return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.ACCEPT).build();
                    }
                });
        // send vote
        electState.becomeCandidate();
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(electState.getLeaderId()).isEqualTo(2)
        );
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(electState.getState()).isEqualTo(RaftStateEnum.FOLLOWER)
        );
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(electState.getTerm().get()).isEqualTo(100)
        );
    }

    @Test
    void reElectSuccessTest() {
        // first mock follower elect, second mock reelect success
        AtomicBoolean followerElectMock = new AtomicBoolean(true);
        Mockito.when(raftClient.sendVote(
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    if (followerElectMock.get()) {
                        Object[] arguments = invocation.getArguments();
                        if (arguments[0].toString().contains(PEER2)) {
                            return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.ACCEPT).build();
                        } else if (arguments[0].toString().contains(PEER3)) {
                            followerElectMock.set(false);
                            return RaftVoteRes.newBuilder()
                                    .setResult(RaftVoteRes.Result.TERM_EXPIRE)
                                    .setLeaderId(2)
                                    .setTerm(100)
                                    .build();
                        } else {
                            return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.ACCEPT).build();
                        }
                    }
                    return RaftVoteRes.newBuilder().setResult(RaftVoteRes.Result.ACCEPT).build();
                });
        // become candidate
        electState.becomeCandidate();
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(electState.getLeaderId()).isEqualTo(2)
        );
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(electState.getState()).isEqualTo(RaftStateEnum.FOLLOWER)
        );
        // simulate heartbeat expire
        electState.setLastLeaderHeartbeatTime(0);
        followerComponent.leaderHeartbeatCheck();
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(electState.getLeaderId()).isEqualTo(0)
        );
    }

    @Test
    void followerReceiveHeartbeatTest() {
        // become follower
        voteExpireTest();
        // mock read index
        Mockito.when(raftClient.readIndex(
                ArgumentMatchers.any(),
                ArgumentMatchers.any()))
                .thenReturn(ReadIndexRes.newBuilder()
                        .setSuccess(true)
                        .setNextIndex(2)
                        .addLogDates(ByteString.EMPTY)
                .build());
        // send heartbeat
        AppendLogReq appendLogReq = AppendLogReq.newBuilder()
                .setLeaderId(2)
                .setTerm(100)
                .setLogIndex(1)
                .build();
        AppendLogRes appendLogRes = raftComponent.appendEntry(appendLogReq);
        assertThat(appendLogRes.getResult()).isEqualTo(AppendLogRes.AppendResult.SUCCESS);
    }

    @Test
    void waitWatermarkSyncTest() {
        voteSuccessTest();
        AtomicBoolean syncCompile = new AtomicBoolean(false);
        Thread.ofVirtual().start(() -> {
            peerWaterMark.waitQuorum(1);
            syncCompile.set(true);
        });
        peerWaterMark.refreshPeerItem(PEER1);
        peerWaterMark.refreshPeerItem(PEER3);
        peerWaterMark.updateLowWaterMark(PEER1, 1);
        peerWaterMark.updateLowWaterMark(PEER3, 2);
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(syncCompile).isTrue());
    }

    @Test
    void brokerRegistryTest() {
        voteSuccessTest();
        BrokerRegisterRequest request = BrokerRegisterRequest.newBuilder()
                .setCluster("test")
                .setId(UUID.randomUUID().toString())
                .setIp("127.0.0.1")
                .build();
        AtomicBoolean success = new AtomicBoolean(false);
        Thread.ofVirtual().start(() -> {
            BrokerRegisterReply registerReply = metaBrokerServiceBlockingStub.brokerRegister(request);
            success.set(registerReply.getSuccess());
        });
        peerWaterMark.refreshPeerItem(PEER2);
        peerWaterMark.refreshPeerItem(PEER3);
        peerWaterMark.updateLowWaterMark(PEER2, 1);
        peerWaterMark.updateLowWaterMark(PEER3, 2);
        await().atMost(Duration.ofSeconds(200)).untilAsserted(() -> assertThat(success.get()).isTrue());
        // get log
        byte[] bytes = logProxy.readIndex(1);
        String s = new String(bytes);
        MetadataDefinition metadataDefinition = new Gson().fromJson(s, MetadataDefinition.class);
        assertThat(metadataDefinition.getMetadataTypeEnum() == MetadataTypeEnum.BROKER).isTrue();
    }

}
