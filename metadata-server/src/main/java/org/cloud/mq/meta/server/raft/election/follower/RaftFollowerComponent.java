package org.cloud.mq.meta.server.raft.election.follower;

import io.grpc.ManagedChannel;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.eventbus.Message;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.raft.RaftServerServiceGrpc;
import org.cloud.mq.meta.raft.ReadIndexReq;
import org.cloud.mq.meta.raft.ReadIndexRes;
import org.cloud.mq.meta.server.raft.client.RaftClient;
import org.cloud.mq.meta.server.raft.common.RaftUtils;
import org.cloud.mq.meta.server.raft.election.ElectState;
import org.cloud.mq.meta.server.raft.election.RaftConstant;
import org.cloud.mq.meta.server.raft.election.RaftStateEnum;
import org.cloud.mq.meta.server.raft.log.LogProxy;

/**
 * follower
 * @author renyansong
 */
@ApplicationScoped
@Slf4j
public class RaftFollowerComponent {

    @Inject
    Scheduler scheduler;

    @Inject
    ElectState electState;

    @Inject
    LogProxy logProxy;

    @Inject
    RaftClient raftClient;

    @ConsumeEvent(RaftConstant.RAFT_STATE_TOPIC)
    public void consumeStateEvent(Message<RaftStateEnum> message) {
        if (message.body() != RaftStateEnum.FOLLOWER) {
            scheduler.unscheduleJob(RaftConstant.HEARTBEAT_LISTEN_SCHEDULER);
            return;
        }
        if (message.body() == RaftStateEnum.FOLLOWER) {
            becomeFollower(electState.getLeaderId(), electState.getTerm().get());
        }
    }

    private void becomeFollower(int leaderId, int term) {
        if (log.isDebugEnabled()) {
            log.debug("id:{}, become follower, now LeaderId:{}, term:{}", RaftUtils.getIdByHost(null), electState.getLeaderId(), electState.getTerm().get());
        }
        // start leader heartbeat listener
        try {
            scheduler.newJob(RaftConstant.HEARTBEAT_LISTEN_SCHEDULER)
                    .setInterval(RaftConstant.HEARTBEAT_INTERVAL_S + "s")
                    .setTask(executionContext -> {
                        leaderHeartbeatCheck();
                    })
                    .schedule();
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("A job with this identity is already scheduled")) {
                log.error("schedule error", e);
            }
            return;
        }
    }

    /**
     * leader Heartbeat check
     */
    public void leaderHeartbeatCheck() {
        if (electState.getState() == RaftStateEnum.FOLLOWER) {
            if (electState.getLastLeaderHeartbeatTime() + RaftConstant.HEARTBEAT_INTERVAL_S * 3000 < System.currentTimeMillis()) {
                electState.becomeCandidate();
            }
        }
    }

    @SuppressWarnings("BusyWait")
    @ConsumeEvent(value = RaftConstant.LOG_SYNC_TOPIC)
    public void followerSyncLogTask(long logIndex) {
        while (electState.getState() != RaftStateEnum.FOLLOWER) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                // ignore...
            }
        }
        long nextIndex = -1;
        while (electState.getState() == RaftStateEnum.FOLLOWER && nextIndex < logIndex) {
            long localLogIndex = logProxy.getLastKey();
            try {
                ManagedChannel channelById = raftClient.getChannelById(electState.getLeaderId());
                if (channelById == null) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        // ignore...
                    }
                    log.warn("get leaderId:{} channel is null", electState.getLeaderId());
                    continue;
                }
                ReadIndexReq readIndexReq = ReadIndexReq.newBuilder()
                        .setTerm(electState.getTerm().get())
                        .setStartIndex(localLogIndex)
                        .setFromHost(RaftUtils.getMyHostName())
                        .build();
                ReadIndexRes readIndexRes = raftClient.readIndex(channelById, readIndexReq);
                if (!readIndexRes.getSuccess()) {
                    if (readIndexRes.getNextIndex() != -1) {
                        while (localLogIndex > readIndexRes.getNextIndex()) {
                            logProxy.deleteByIndex(localLogIndex);
                            localLogIndex--;
                        }
                    }
                }
                if (readIndexRes.getSuccess()) {
                    nextIndex = readIndexRes.getNextIndex();
                    // append log log
                    logProxy.appendLog(logIndex, readIndexRes.getLogDates(0).toByteArray());
                }
            } catch (Exception e) {
                log.error("follower sync log thread error catch, sleep...", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    // ignore...
                }
            }
        }
    }

}
