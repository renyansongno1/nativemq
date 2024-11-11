package org.cloud.mq.meta.server.raft.election.leader;

import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.eventbus.Message;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.raft.AppendLogReq;
import org.cloud.mq.meta.server.common.MetadataConstant;
import org.cloud.mq.meta.server.common.MetadataDefinition;
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

    private static final Gson GSON = new Gson();

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

    @Inject
    EventBus eventBus;

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

        reloadRaftLog();

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

    public void reloadRaftLog() {
        Thread.ofVirtual().name("reload_raft_load_v_thread").start(() -> {
            // reload log
            // TODO: 2023/9/24 snapshot log
            Map<Long, byte[]> allLogData = logProxy.getAllLogData();
            for (Map.Entry<Long, byte[]> logIndexValEntry : allLogData.entrySet()) {
                eventBus.publish(MetadataConstant.METADATA_CHANGE_TOPIC, GSON.fromJson(new String(logIndexValEntry.getValue()), MetadataDefinition.class));
            }
            electState.setReady(true);
        });
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
