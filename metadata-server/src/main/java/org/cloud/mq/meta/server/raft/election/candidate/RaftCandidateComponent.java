package org.cloud.mq.meta.server.raft.election.candidate;

import io.grpc.ManagedChannel;
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

    @ConsumeEvent(RaftConstant.RAFT_STATE_TOPIC)
    public void consumeStateEvent(Message<RaftStateEnum> message) {
        if (message.body() == RaftStateEnum.CANDIDATE) {
            becomeCandidateInit(RaftUtils.getIdByHost(null), logProxy.getLastKey(), electState.getTerm());
        }
    }

    public void becomeCandidateInit(int myId, long logIndex, AtomicInteger term) {
        if (log.isDebugEnabled()) {
            log.debug("id:{}, become candidate", myId);
        }
        vote(logIndex, myId, term);
    }

    @SuppressWarnings("BusyWait")
    public void vote(long localLogIndex, int myId, AtomicInteger term) {
        while (electState.getState() == RaftStateEnum.CANDIDATE) {
            try {
                Thread.sleep(new Random().nextInt(RaftConstant.MIN_VOTE_TIME_INTERVAL_MS, RaftConstant.MAX_VOTE_TIME_INTERVAL_MS));
            } catch (InterruptedException e) {
                // ignore
            }
            if (raftClient.getAllChannel().isEmpty()) {
                continue;
            }
            // build vote req
            RaftVoteReq raftVoteReq = RaftVoteReq.newBuilder()
                    // always vote for myself
                    .setLeaderId(myId)
                    .setLogIndex(localLogIndex)
                    .setTerm(term.addAndGet(1))
                    .build();
            if (log.isDebugEnabled()) {
                log.debug("start req vote, vote body:{}", raftVoteReq);
            }
            int vote = 1;
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
                    }
                } catch (Exception e) {
                    log.error("vote for channel:{}, error", managedChannel, e);
                }
            }
            if (vote > (raftClient.getAllChannel().size() + 1)/2) {
                electState.becomeLeader();
                break;
            }
        }
    }

}
