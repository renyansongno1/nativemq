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

    private long lastLeaderHeartbeatTime;

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
            log.debug("id:{}, become follower", RaftUtils.getIdByHost(null));
        }
        electState.becomeFollower(leaderId, term);
        // start leader heartbeat listener
        try {
            scheduler.newJob(RaftConstant.HEARTBEAT_LISTEN_SCHEDULER)
                    .setInterval(RaftConstant.HEARTBEAT_INTERVAL_S + "s")
                    .setTask(executionContext -> {
                        if (electState.getState() == RaftStateEnum.FOLLOWER) {
                            if (lastLeaderHeartbeatTime + RaftConstant.HEARTBEAT_INTERVAL_S * 3000 < System.currentTimeMillis()) {
                                electState.becomeCandidate();
                            }
                        }
                    })
                    .schedule();
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("A job with this identity is already scheduled")) {
                log.error("schedule error", e);
            }
            return;
        }
        // TODO: 2023/8/29 receive Heartbeat
        new Thread(this::followerSyncLogTask, "follower-log-sync").start();
    }

    @SuppressWarnings("BusyWait")
    private void followerSyncLogTask() {
        while (electState.getState() == RaftStateEnum.FOLLOWER) {
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
                        .setFromHost(System.getenv("HOSTNAME"))
                        .build();
                RaftServerServiceGrpc.RaftServerServiceBlockingStub raftServerServiceBlockingStub = RaftServerServiceGrpc.newBlockingStub(channelById);
                ReadIndexRes readIndexRes = raftServerServiceBlockingStub.readIndex(readIndexReq);
                if (!readIndexRes.getSuccess()) {
                    if (readIndexRes.getNextIndex() != -1) {
                        while (localLogIndex > readIndexRes.getNextIndex()) {
                            logProxy.deleteByIndex(localLogIndex);
                            localLogIndex--;
                        }
                    }
                }
                if (readIndexRes.getSuccess()) {
                    // append log log
                    logProxy.appendLog(readIndexRes.getNextIndex(), readIndexRes.getLogDates(0).toByteArray());
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

    public void receiveHeartbeat() {
        this.lastLeaderHeartbeatTime = System.currentTimeMillis();
    }
}