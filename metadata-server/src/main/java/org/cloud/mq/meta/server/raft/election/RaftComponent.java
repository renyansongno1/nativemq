package org.cloud.mq.meta.server.raft.election;

import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.eventbus.EventBus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.raft.*;
import org.cloud.mq.meta.server.common.MetadataConstant;
import org.cloud.mq.meta.server.common.MetadataDefinition;
import org.cloud.mq.meta.server.raft.common.RaftUtils;
import org.cloud.mq.meta.server.raft.log.LogProxy;
import org.cloud.mq.meta.server.raft.client.RaftClient;
import org.cloud.mq.meta.server.raft.peer.PeerWaterMark;

import java.util.Map;

/**
 * Raft Elect
 * @author renyansong
 */
@ApplicationScoped
@Startup
@Slf4j
@Getter
@RegisterForReflection
public class RaftComponent {

    private static final Gson GSON = new Gson();

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
        reloadRaftLog();
        Thread.ofVirtual().start(() -> electState.becomeCandidate());
    }

    public void reloadRaftLog() {
        log.info("start reload raft log from db...");
        // reload log
        // TODO: 2023/9/24 snapshot log
        Map<Long, byte[]> allLogData = logProxy.getAllLogData();
        log.info("find :{} entry log data", allLogData.entrySet().size());
        for (Map.Entry<Long, byte[]> logIndexValEntry : allLogData.entrySet()) {
            bus.publish(MetadataConstant.METADATA_CHANGE_TOPIC, GSON.fromJson(new String(logIndexValEntry.getValue()), MetadataDefinition.class));
        }
        electState.setReady(true);
    }

    /**
     * receive other peer vote
     * @param request vote req
     * @return res
     */
    public RaftVoteRes receiveVote(RaftVoteReq request) {
        if (log.isDebugEnabled()) {
            log.debug("receive vote:{}, now state:{}, term:{}, leaderId:{}", request, electState.getState(), electState.getTerm(), electState.getLeaderId());
        }
        // only candidate can vote
        if (electState.getState() != RaftStateEnum.CANDIDATE) {
            return RaftVoteRes.newBuilder()
                    .setResult(RaftVoteRes.Result.REJECT)
                    .setLeaderId(electState.getLeaderId())
                    .setTerm(electState.getTerm().get())
                    .build();
        }
        if (request.getTerm() > electState.getTerm().get()) {
            // accept vote
            electState.becomeFollower(request.getLeaderId(), request.getTerm());
            return RaftVoteRes.newBuilder()
                    .setResult(RaftVoteRes.Result.ACCEPT)
                    .build();
        // term expire
        } else if (request.getTerm() < electState.getTerm().get()) {
            return RaftVoteRes.newBuilder()
                    .setResult(RaftVoteRes.Result.TERM_EXPIRE)
                    .setLeaderId(electState.getLeaderId())
                    .setTerm(electState.getTerm().get())
                    .build();
        // same term but higher max un commit log id
        } else if (request.getMaxUnCommitLogId() > logProxy.getLastKey()) {
            // accept vote
            electState.becomeFollower(request.getLeaderId(), request.getTerm());
            return RaftVoteRes.newBuilder()
                    .setResult(RaftVoteRes.Result.ACCEPT)
                    .build();
        }
        return RaftVoteRes.newBuilder()
                .setResult(RaftVoteRes.Result.REJECT)
                .setLeaderId(electState.getLeaderId())
                .setTerm(electState.getTerm().get())
                .build();
    }

    public synchronized AppendLogRes appendEntry(AppendLogReq item) {
        if (log.isDebugEnabled()) {
            log.debug("receive append entry:{}", item);
        }
        if (item.getLogData().isEmpty()) {
            if (item.getTerm() > electState.getTerm().get()) {
                electState.becomeFollower(item.getLeaderId(), item.getTerm());
                // waitForSync(item.getLogIndex());
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
                electState.setLastLeaderHeartbeatTime(System.currentTimeMillis());
                // waitForSync(item.getLogIndex());
                return AppendLogRes.newBuilder()
                        .setResult(AppendLogRes.AppendResult.SUCCESS)
                        .setLeaderId(item.getLeaderId())
                        .setNextIndex(logProxy.getLastKey() + 1)
                        .setMyId(RaftUtils.getIdByHost(null))
                        .build();
            }
            log.error("not follower state receive heartbeat:{}", item);
            return AppendLogRes.newBuilder()
                    .setResult(AppendLogRes.AppendResult.SUCCESS)
                    .setLeaderId(item.getLeaderId())
                    .setNextIndex(logProxy.getLastKey() + 1)
                    .setMyId(RaftUtils.getIdByHost(null))
                    .build();
        }
        // leader write data
        long nextKey = logProxy.getLastKey() + 1;
        logProxy.appendLog(nextKey, item.getLogData().toByteArray());
        // sync log
        peerWaterMark.syncHighWaterMark(logProxy.getLastKey());
        return AppendLogRes.newBuilder()
                .setResult(AppendLogRes.AppendResult.SUCCESS)
                .setLeaderId(item.getLeaderId())
                .setNextIndex(nextKey)
                .setMyId(RaftUtils.getIdByHost(null))
                .build();
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
        if (log.isDebugEnabled()) {
            log.debug("read index:{}, peerWaterMark:{}", readIndexReq, peerWaterMark.getPeerMap());
        }
        long waterMark =  peerWaterMark.getWaterMark(readIndexReq.getFromHost());
        if (waterMark == -1L) {
            return ReadIndexRes.newBuilder()
                    .setSuccess(false)
                    .setNextIndex(0)
                    .build();
        }
        if (peerWaterMark.getHighWaterMark() < readIndexReq.getStartIndex()) {
            return ReadIndexRes.newBuilder()
                    .setSuccess(false)
                    .setNextIndex(peerWaterMark.getHighWaterMark() + 1)
                    .build();
        }
        byte[] bytes = logProxy.readIndex(readIndexReq.getStartIndex());
        long nextIndex = readIndexReq.getStartIndex() + 1;
        ReadIndexRes indexRes = ReadIndexRes.newBuilder()
                .setSuccess(true)
                .setLogDates(ByteString.copyFrom(bytes))
                .setNextIndex(nextIndex)
                .build();
        if (log.isDebugEnabled()) {
            log.debug("send read res:{} for req:{}", indexRes, readIndexReq);
        }
        return indexRes;
    }

    /**
     * pre destroy clean method
     */
    @PreDestroy
    public void destroy() {}
}
