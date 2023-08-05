package org.cloud.mq.meta.server.raft.election;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduler;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.raft.*;
import org.cloud.mq.meta.server.raft.common.RaftUtils;
import org.cloud.mq.meta.server.raft.log.LogProxy;
import org.cloud.mq.meta.server.raft.client.RaftClient;
import org.cloud.mq.meta.server.raft.peer.PeerWaterMark;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Raft Elect
 * @author renyansong
 */
@ApplicationScoped
@Startup
@Slf4j
public class ElectComponent {

    /**
     * vote min interval
     */
    private static final int MIN_VOTE_TIME_INTERVAL_MS = 300;

    /**
     * vote max interval
     */
    private static final int MAX_VOTE_TIME_INTERVAL_MS = 1500;

    /**
     * leader Heartbeat interval
     */
    public static final int HEARTBEAT_INTERVAL_MS = 5000;

    /**
     * heartbeat scheduler every HEARTBEAT_INTERVAL_MS
     */
    private static final String HEARTBEAT_SCHEDULER = "heartbeat_scheduler";

    /**
     * follower listen leader heartbeat
     */
    private static final String HEARTBEAT_LISTEN_SCHEDULER = "heartbeat_listen_scheduler";

    @Inject
    RaftClient raftClient;

    @Inject
    LogProxy logProxy;

    @Inject
    Scheduler scheduler;

    @Inject
    PeerWaterMark peerWaterMark;

    /**
     * now term
     */
    private int term;

    private int leaderId = -1;

    private int myId;

    private long lastLeaderHeartbeatTime;

    /**
     * role
     */
    private RaftStateEnum state = RaftStateEnum.CANDIDATE;

    @PostConstruct
    public void init() {
        // init read log
        long localLogIndex = getLocalLogIndex();

        new Thread(() -> {
            // init vote
            vote(localLogIndex);
        },"elect-thread").start();
    }

    private void vote(long localLogIndex) {
        while (state == RaftStateEnum.CANDIDATE) {
            try {
                Thread.sleep(new Random().nextInt(MIN_VOTE_TIME_INTERVAL_MS, MAX_VOTE_TIME_INTERVAL_MS));
            } catch (InterruptedException e) {
                // ignore
            }
            if (raftClient.getAllChannel().isEmpty()) {
                continue;
            }
            myId = RaftUtils.getIdByHost(null);
            // build vote req
            RaftVoteReq raftVoteReq = RaftVoteReq.newBuilder()
                    // always vote for myself
                    .setLeaderId(myId)
                    .setLogIndex(localLogIndex)
                    .setTerm(0)
                    .build();
            int vote = 1;
            for (ManagedChannel managedChannel : raftClient.getAllChannel()) {
                try {
                    RaftServerServiceGrpc.RaftServerServiceBlockingStub raftServerServiceBlockingStub = RaftServerServiceGrpc.newBlockingStub(managedChannel);
                    RaftVoteRes raftVoteRes = raftServerServiceBlockingStub.requestVote(raftVoteReq);
                    if (raftVoteRes.getResult() == RaftVoteRes.Result.ACCEPT) {
                        vote++;
                    } else if (raftVoteRes.getResult() == RaftVoteRes.Result.TERM_EXPIRE) {
                        if (raftVoteReq.getTerm() > term) {
                            // vote over by higher term
                            becomeFollower(raftVoteRes.getLeaderId(), raftVoteReq.getTerm());
                            return;
                        }
                    }
                } catch (Exception e) {
                    log.error("vote for channel:{}, error", managedChannel, e);
                }
            }
            if (vote > (raftClient.getAllChannel().size() + 1)/2) {
                becomeLeader();
                break;
            }
        }
    }

    public RaftVoteRes receiveVote(RaftVoteReq request) {
        if (request.getTerm() > term || leaderId == -1) {
            // accept vote
            becomeFollower(leaderId, request.getTerm());
            return RaftVoteRes.newBuilder()
                    .setResult(RaftVoteRes.Result.ACCEPT)
                    .build();
        } else if (request.getTerm() < term) {
            return RaftVoteRes.newBuilder()
                    .setResult(RaftVoteRes.Result.TERM_EXPIRE)
                    .setLeaderId(leaderId)
                    .setTerm(term)
                    .build();
        }
        return RaftVoteRes.newBuilder()
                .setResult(RaftVoteRes.Result.REJECT)
                .setLeaderId(leaderId)
                .setTerm(term)
                .build();
    }

    public Multi<AppendLogRes> appendEntry(Multi<AppendLogReq> request) {
        List<AppendLogRes> resList = new ArrayList<>();
        request.onItem().invoke(item -> {
            if (item.getLogData().isEmpty()) {
                // heartbeat
                if (item.getTerm() > term) {
                    becomeFollower(item.getLeaderId(), item.getTerm());
                    return;
                }
                if (item.getTerm() < term) {
                    resList.add(AppendLogRes.newBuilder()
                            .setResult(AppendLogRes.AppendResult.NOT_LEADER)
                            .setLeaderId(leaderId)
                            .build());
                    return;
                }
                // normal term
                if (state == RaftStateEnum.COORDINATOR) {
                    becomeFollower(item.getLeaderId(), item.getTerm());
                    return;
                }
                if (state == RaftStateEnum.FOLLOWER) {
                    lastLeaderHeartbeatTime = System.currentTimeMillis();
                    return;
                }
            }
            // log append not need
        });
        return Multi.createFrom().items(resList.stream());
    }

    public ReadIndexRes readIndex(ReadIndexReq readIndexReq) {
        if (state != RaftStateEnum.LEADER) {
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



    private void becomeLeader() {
        if (log.isDebugEnabled()) {
            log.debug("id:{}, become leader", myId);
        }
        state = RaftStateEnum.LEADER;
        // first term + 1
        term++;
        // second notify every node
        // wait all node replay
        heartbeat();
        if (state != RaftStateEnum.LEADER) {
            return;
        }
        // heartbeat schedule
        scheduler.newJob(HEARTBEAT_SCHEDULER)
                .setInterval(HEARTBEAT_INTERVAL_MS + "ms")
                .setTask(executionContext -> {
                    // do HEARTBEAT every HEARTBEAT_SCHEDULER seconds
                    heartbeat();
                })
                .schedule();
        // sync log
        peerWaterMark.syncHighWaterMark(getLocalLogIndex());
    }

    private void heartbeat() {
        if (state != RaftStateEnum.LEADER) {
            scheduler.unscheduleJob(HEARTBEAT_SCHEDULER);
            return;
        }
        final int[] successCount = {1};
        for (ManagedChannel managedChannel : raftClient.getAllChannel()) {
            try {
                RaftServerServiceGrpc.RaftServerServiceStub raftServerServiceStub = RaftServerServiceGrpc.newStub(managedChannel).withDeadlineAfter(2, TimeUnit.SECONDS);
                StreamObserver<AppendLogReq> appendLogReqStreamObserver = raftServerServiceStub.appendEntries(new StreamObserver<AppendLogRes>() {
                    @Override
                    public void onNext(AppendLogRes appendLogRes) {
                        if (appendLogRes.getResult() == AppendLogRes.AppendResult.NOT_LEADER) {
                            // other leader is higher term
                            becomeFollower(appendLogRes.getLeaderId(), appendLogRes.getTerm());
                            return;
                        }
                        if (appendLogRes.getResult() == AppendLogRes.AppendResult.INCONSISTENCY) {
                            // TODO: 2023/7/31 log error
                        }
                        if (appendLogRes.getResult() == AppendLogRes.AppendResult.SUCCESS) {
                            if (log.isDebugEnabled()) {
                                log.debug("receive:{}, success heartbeat response", managedChannel);
                            }
                            successCount[0]++;
                            peerWaterMark.refreshPeerItem(raftClient.getPeerAddrByChannel(managedChannel), appendLogRes.getNextIndex());
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("send heaert beat error for {}", managedChannel, throwable);
                    }

                    @Override
                    public void onCompleted() {
                        if (log.isDebugEnabled()) {
                            log.debug("send heartbeat for:{}, completed", managedChannel);
                        }
                    }
                });
                // send msg
                appendLogReqStreamObserver.onNext(AppendLogReq.newBuilder()
                        .setLeaderId(myId)
                        .setLogIndex(getLocalLogIndex())
                        .setTerm(term)
                        // null log data on behalf of Heartbeat
                        .setLogData(ByteString.copyFrom(new byte[]{}))
                        .build());

                appendLogReqStreamObserver.onCompleted();
            } catch (Exception e) {
                log.error("send heartbeat error for channel:{}", managedChannel, e);
            }
        }
        // check heartbeat
        if (successCount[0] < (raftClient.getAllChannel().size() + 1) / 2) {
            becomeCandidate();
        }
    }

    private void becomeCandidate() {
        if (log.isDebugEnabled()) {
            log.debug("id:{}, become candidate", myId);
        }
        state = RaftStateEnum.CANDIDATE;
        scheduler.unscheduleJob(HEARTBEAT_SCHEDULER);
        scheduler.unscheduleJob(HEARTBEAT_LISTEN_SCHEDULER);
        vote(getLocalLogIndex());
    }

    private void becomeFollower(int leaderId, int term) {
        if (log.isDebugEnabled()) {
            log.debug("id:{}, become follower", myId);
        }
        state = RaftStateEnum.FOLLOWER;
        this.leaderId = leaderId;
        this.term = term;
        // stop old term log heaertbeat scheduler
        scheduler.unscheduleJob(HEARTBEAT_SCHEDULER);
        // start leader heartbeat listener
        scheduler.newJob(HEARTBEAT_LISTEN_SCHEDULER)
                .setInterval(HEARTBEAT_INTERVAL_MS + "ms")
                .setTask(executionContext -> {
                    if (state == RaftStateEnum.FOLLOWER) {
                        if (lastLeaderHeartbeatTime + HEARTBEAT_INTERVAL_MS * 3 < System.currentTimeMillis()) {
                            becomeCandidate();
                        }
                    }
                })
                .schedule();
        new Thread(this::followerSyncLogTask, "follower-log-sync").start();
    }

    private void followerSyncLogTask() {
        while (state == RaftStateEnum.FOLLOWER) {
            long localLogIndex = getLocalLogIndex();
            try {
                ManagedChannel channelById = raftClient.getChannelById(leaderId);
                ReadIndexReq readIndexReq = ReadIndexReq.newBuilder()
                        .setTerm(term)
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
                log.error("follower sync log thread error catch", e);
            }
        }
    }

    /**
     * read local log for index
     * @return log index
     */
    private long getLocalLogIndex() {
        return logProxy.getLastKey();
    }

    /**
     * pre destroy clean method
     */
    @PreDestroy
    public void destroy() {
        scheduler.unscheduleJob(HEARTBEAT_SCHEDULER);
    }
}
