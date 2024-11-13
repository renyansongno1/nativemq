package org.cloud.mq.meta.server.raft.election.candidate;

import io.grpc.ManagedChannel;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.eventbus.Message;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.raft.RaftVoteReq;
import org.cloud.mq.meta.raft.RaftVoteRes;
import org.cloud.mq.meta.server.raft.client.RaftClient;
import org.cloud.mq.meta.server.raft.common.RaftUtils;
import org.cloud.mq.meta.server.raft.election.ElectState;
import org.cloud.mq.meta.server.raft.election.RaftConstant;
import org.cloud.mq.meta.server.raft.election.RaftStateEnum;
import org.cloud.mq.meta.server.raft.log.LogProxy;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
@Slf4j
public class RaftCandidateComponent {

    @Inject
    RaftClient raftClient;

    @Inject
    LogProxy logProxy;

    @Inject
    ElectState electState;

    @Inject
    Scheduler scheduler;

    @ConsumeEvent(RaftConstant.RAFT_STATE_TOPIC)
    public void consumeStateEvent(Message<RaftStateEnum> message) {
        if (message.body() == RaftStateEnum.CANDIDATE) {
            Thread.ofVirtual().start(() -> becomeCandidateInit(RaftUtils.getIdByHost(null), logProxy.getLastKey(), electState.getTerm()));
        }
    }

    public void becomeCandidateInit(int myId, long logIndex, AtomicInteger term) {
        if (log.isDebugEnabled()) {
            log.debug("id:{}, become candidate", myId);
        }
        // from follower to candidate
        scheduler.unscheduleJob(RaftConstant.HEARTBEAT_LISTEN_SCHEDULER);
        try {
            vote(logIndex, myId, term);
        } catch (Exception e) {
            log.error("vote error", e);
            electState.becomeFollower(-1, electState.getTerm().incrementAndGet());
        }
    }

    @SuppressWarnings("BusyWait")
    public void vote(long localLogIndex, int myId, AtomicInteger term) {
        while (electState.getState() == RaftStateEnum.CANDIDATE) {
            try {
                Thread.sleep(new Random().nextInt(RaftConstant.MIN_VOTE_TIME_INTERVAL_MS, RaftConstant.MAX_VOTE_TIME_INTERVAL_MS));
            } catch (InterruptedException e) {
                // ignore
            }
            if (raftClient.getAllChannel().size() < 2) {
                continue;
            }
            if (electState.getState() != RaftStateEnum.CANDIDATE) {
                return;
            }
            // build vote req
            RaftVoteReq raftVoteReq = RaftVoteReq.newBuilder()
                    // always vote for myself
                    .setLeaderId(myId)
                    .setMaxUnCommitLogId(localLogIndex)
                    .setTerm(term.addAndGet(1))
                    .build();
            if (log.isDebugEnabled()) {
                log.debug("start req vote, vote body:{}", raftVoteReq);
            }
            int vote = 1;
            int rejectVote = 0;
            for (ManagedChannel managedChannel : raftClient.getAllChannel()) {
                try {
                    RaftVoteRes raftVoteRes = raftClient.sendVote(managedChannel, raftVoteReq);
                    if (raftVoteRes.getResult() == RaftVoteRes.Result.ACCEPT) {
                        vote++;
                    } else if (raftVoteRes.getResult() == RaftVoteRes.Result.TERM_EXPIRE) {
                        if (raftVoteRes.getTerm() > term.get()) {
                            // vote over by higher term
                            electState.becomeFollower(raftVoteRes.getLeaderId(), raftVoteRes.getTerm());
                            return;
                        }
                    } else {
                        // reject vote
                        rejectVote++;
                    }
                } catch (Exception e) {
                    log.error("vote for channel:{}, error", managedChannel, e);
                }
            }
            if (vote > (raftClient.getAllChannel().size() + 1)/2) {
                electState.becomeLeader();
                break;
            }
            if (rejectVote > (raftClient.getAllChannel().size() + 1)/2) {
                vote(localLogIndex, myId, term);
            }
        }
    }

}
