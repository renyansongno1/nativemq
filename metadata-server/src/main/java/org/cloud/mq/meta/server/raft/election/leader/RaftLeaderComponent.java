package org.cloud.mq.meta.server.raft.election.leader;

import com.google.protobuf.ByteString;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.eventbus.Message;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.raft.AppendLogReq;
import org.cloud.mq.meta.server.raft.common.RaftUtils;
import org.cloud.mq.meta.server.raft.election.ElectState;
import org.cloud.mq.meta.server.raft.election.RaftConstant;
import org.cloud.mq.meta.server.raft.election.RaftStateEnum;
import org.cloud.mq.meta.server.raft.election.heartbeat.HeartbeatComponent;
import org.cloud.mq.meta.server.raft.log.LogProxy;
import org.cloud.mq.meta.server.raft.peer.PeerWaterMark;

import java.util.Map;

@ApplicationScoped
@Slf4j
public class RaftLeaderComponent {

    @Inject
    Scheduler scheduler;

    @Inject
    ElectState electState;

    @Inject
    HeartbeatComponent heartbeatComponent;

    @Inject
    PeerWaterMark peerWaterMark;

    @Inject
    LogProxy logProxy;

    @ConsumeEvent(RaftConstant.RAFT_STATE_TOPIC)
    public void consumeStateEvent(Message<RaftStateEnum> message) {
        if (message.body() != RaftStateEnum.LEADER) {
            scheduler.unscheduleJob(RaftConstant.HEARTBEAT_SCHEDULER);
            return;
        }
        if (message.body() == RaftStateEnum.LEADER) {
            becomeLeader();
        }
    }

    public void becomeLeader() {
        if (log.isDebugEnabled()) {
            log.debug("id:{}, become leader", RaftUtils.getIdByHost(null));
        }
        if (electState.getState() != RaftStateEnum.LEADER) {
            return;
        }

        // heartbeat schedule
        scheduler.newJob(RaftConstant.HEARTBEAT_SCHEDULER)
                .setInterval(RaftConstant.HEARTBEAT_INTERVAL_S + "s")
                .setTask(executionContext -> {
                    // do HEARTBEAT every HEARTBEAT_SCHEDULER seconds
                    heartbeat();
                })
                .schedule();
        // sync log
        peerWaterMark.syncHighWaterMark(logProxy.getLastKey());
    }

    private void heartbeat() {
        if (electState.getState() != RaftStateEnum.LEADER) {
            scheduler.unscheduleJob(RaftConstant.HEARTBEAT_SCHEDULER);
            return;
        }
        heartbeatComponent.heartbeat(AppendLogReq.newBuilder()
                .setLeaderId(RaftUtils.getIdByHost(null))
                .setLogIndex(logProxy.getLastKey())
                .setTerm(electState.getTerm().get())
                // null log data on behalf of Heartbeat
                .setLogData(ByteString.copyFrom(new byte[]{}))
                .build());
    }

}
