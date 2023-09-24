package org.cloud.mq.meta.server.raft.election;

import com.google.protobuf.ByteString;
import io.quarkus.runtime.Startup;
import io.vertx.core.eventbus.EventBus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.raft.*;
import org.cloud.mq.meta.server.raft.common.RaftUtils;
import org.cloud.mq.meta.server.raft.log.LogProxy;
import org.cloud.mq.meta.server.raft.client.RaftClient;
import org.cloud.mq.meta.server.raft.peer.PeerWaterMark;

/**
 * Raft Elect
 * @author renyansong
 */
@ApplicationScoped
@Startup
@Slf4j
@Getter
public class RaftComponent {

    @Inject
    RaftClient raftClient;

    @Inject
    LogProxy logProxy;

    @Inject
    PeerWaterMark peerWaterMark;

    @Inject
    ElectState electState;

    @Inject
    EventBus bus;

    @PostConstruct
    public void init() {
        electState.becomeCandidate();
    }

    /**
     * receive other peer vote
     * @param request vote req
     * @return res
     */
    public RaftVoteRes receiveVote(RaftVoteReq request) {
        if (request.getTerm() > electState.getTerm().get()
                || electState.getLeaderId() == -1) {
            // accept vote
            electState.becomeFollower(request.getLeaderId(), request.getTerm());
            return RaftVoteRes.newBuilder()
                    .setResult(RaftVoteRes.Result.ACCEPT)
                    .build();
        } else if (request.getTerm() < electState.getTerm().get()) {
            return RaftVoteRes.newBuilder()
                    .setResult(RaftVoteRes.Result.TERM_EXPIRE)
                    .setLeaderId(electState.getLeaderId())
                    .setTerm(electState.getTerm().get())
                    .build();
        }
        return RaftVoteRes.newBuilder()
                .setResult(RaftVoteRes.Result.REJECT)
                .setLeaderId(electState.getLeaderId())
                .setTerm(electState.getTerm().get())
                .build();
    }

    public AppendLogRes appendEntry(AppendLogReq item) {
        if (item.getLogData().isEmpty()) {
            if (item.getTerm() > electState.getTerm().get()) {
                electState.becomeFollower(item.getLeaderId(), item.getTerm());
                bus.publish(RaftConstant.LOG_SYNC_TOPIC, item.getLogIndex());
                waitForSync(item.getLogIndex());
                return AppendLogRes.newBuilder()
                        .setResult(AppendLogRes.AppendResult.SUCCESS)
                        .setLeaderId(item.getLeaderId())
                        .setNextIndex(logProxy.getLastKey() + 1)
                        .setMyId(RaftUtils.getIdByHost(null))
                        .build();
            }
            if (item.getTerm() < electState.getTerm().get()) {
                return AppendLogRes.newBuilder()
                        .setResult(AppendLogRes.AppendResult.NOT_LEADER)
                        .setLeaderId(electState.getLeaderId())
                        .setNextIndex(logProxy.getLastKey() + 1)
                        .setMyId(RaftUtils.getIdByHost(null))
                        .build();
            }
            if (electState.getState() == RaftStateEnum.FOLLOWER) {
                bus.publish(RaftConstant.LOG_SYNC_TOPIC, item.getLogIndex());
                electState.setLastLeaderHeartbeatTime(System.currentTimeMillis());
                waitForSync(item.getLogIndex());
                return AppendLogRes.newBuilder()
                        .setResult(AppendLogRes.AppendResult.SUCCESS)
                        .setLeaderId(item.getLeaderId())
                        .setNextIndex(logProxy.getLastKey() + 1)
                        .setMyId(RaftUtils.getIdByHost(null))
                        .build();
            }
        }
        // leader write data
        return null;
    }

    @SuppressWarnings("BusyWait")
    private void waitForSync(long target) {
        while (logProxy.getLastKey() < target) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    public ReadIndexRes readIndex(ReadIndexReq readIndexReq) {
        if (electState.getState() != RaftStateEnum.LEADER) {
            if (log.isDebugEnabled()) {
                log.debug("read index not leader for:{}", readIndexReq);
            }
            return ReadIndexRes.newBuilder()
                    .setSuccess(false)
                    .build();
        }
        long waterMark =  peerWaterMark.getWaterMark(readIndexReq.getFromHost());
        if (waterMark == -1L || waterMark < readIndexReq.getStartIndex()) {
            return ReadIndexRes.newBuilder()
                    .setSuccess(false)
                    .setNextIndex(waterMark)
                    .build();
        }
        byte[] bytes = logProxy.readIndex(readIndexReq.getStartIndex());
        long nextIndex = readIndexReq.getStartIndex() + 1;
        ReadIndexRes res = ReadIndexRes.newBuilder()
                .setSuccess(true)
                .setLogDates(0, ByteString.copyFrom(bytes))
                .setNextIndex(nextIndex)
                .build();
        peerWaterMark.updateLowWaterMark(readIndexReq.getFromHost(), readIndexReq.getStartIndex());
        return res;
    }

    /**
     * pre destroy clean method
     */
    @PreDestroy
    public void destroy() {}
}
