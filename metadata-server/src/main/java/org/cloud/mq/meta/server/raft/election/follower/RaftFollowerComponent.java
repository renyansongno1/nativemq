package org.cloud.mq.meta.server.raft.election.follower;

import io.grpc.ManagedChannel;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.eventbus.Message;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.raft.ReadIndexReq;
import org.cloud.mq.meta.raft.ReadIndexRes;
import org.cloud.mq.meta.server.raft.client.RaftClient;
import org.cloud.mq.meta.server.raft.common.RaftUtils;
import org.cloud.mq.meta.server.raft.election.ElectState;
import org.cloud.mq.meta.server.raft.election.RaftConstant;
import org.cloud.mq.meta.server.raft.election.RaftStateEnum;
import org.cloud.mq.meta.server.raft.log.LogProxy;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * follower
 * @author renyansong
 */
@ApplicationScoped
@Slf4j
public class RaftFollowerComponent {

    private final AtomicBoolean syncTaskStarted = new AtomicBoolean(false);

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
            becomeFollower();
        }
    }

    private void becomeFollower() {
        if (log.isDebugEnabled()) {
            log.debug("id:{}, become follower, now LeaderId:{}, term:{}", RaftUtils.getIdByHost(null), electState.getLeaderId(), electState.getTerm().get());
        }
        // reset heartbeat time
        electState.setLastLeaderHeartbeatTime(System.currentTimeMillis());
        // start leader heartbeat listener
        try {
            scheduler.newJob(RaftConstant.HEARTBEAT_LISTEN_SCHEDULER)
                    .setInterval(RaftConstant.HEARTBEAT_INTERVAL_S + "s")
                    .setTask(executionContext -> {
                        leaderHeartbeatCheck();
                    })
                    .schedule();

            // start follower sync log task
            Thread.ofVirtual().start(this::followerSyncLogTask);
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("A job with this identity is already scheduled")) {
                log.error("schedule error", e);
            }
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
    public void followerSyncLogTask() {
        if (log.isDebugEnabled()) {
            log.debug("followerSyncLogTask... started? :{}", syncTaskStarted.get());
        }
        if (syncTaskStarted.get()) {
            log.warn("followerSyncLogTask started, ignore...");
            return;
        }
        if (syncTaskStarted.compareAndExchange(false, true)) {
            log.warn("followerSyncLogTask start clash... ignore, now state:{}", syncTaskStarted.get());
            return;
        }
        log.info("followerSyncLogTask start...");
        while (electState.getState() == RaftStateEnum.FOLLOWER) {
            long localLogIndex = logProxy.getLastKey();
            if (log.isDebugEnabled()) {
                log.debug("start sync log, now state:{}, localLogIndex:{}", electState.getState(), localLogIndex);
            }
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
                        .setStartIndex(localLogIndex + 1)
                        .setFromHost(RaftUtils.getMyHostName())
                        .build();
                ReadIndexRes readIndexRes = raftClient.readIndex(channelById, readIndexReq);
                if (!readIndexRes.getSuccess()) {
                    // zero is not complete heartbeat
                    while (readIndexRes.getNextIndex() != 0 && localLogIndex >= readIndexRes.getNextIndex()) {
                        log.warn("sync log find error log, now local log index:{}, > leader next index:{}", localLogIndex, readIndexRes.getNextIndex());
                        logProxy.deleteByIndex(localLogIndex);
                        localLogIndex--;
                    }
                    // sleep
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        // ignore...
                    }
                }
                if (readIndexRes.getSuccess()) {
                    // append log
                    logProxy.appendLog(localLogIndex + 1, readIndexRes.getLogDates().toByteArray());
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
        syncTaskStarted.set(false);
    }

}
