package org.cloud.mq.meta.server.raft.election;

import io.vertx.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.server.raft.common.RaftUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
@Slf4j
public class ElectState {
    /**
     * state handle
     */
    private final static AtomicReference<RaftStateEnum> STATE = new AtomicReference<>();

    @Inject
    EventBus bus;

    @Getter
    private final AtomicInteger term = new AtomicInteger(0);

    @Getter
    @Setter
    private int leaderId = -1;

    @Getter
    @Setter
    private long lastLeaderHeartbeatTime;

    @Getter
    @Setter
    private volatile boolean ready = false;

    /**
     * becomeCandidate
     */
    public void becomeCandidate() {
        if (log.isDebugEnabled()) {
            log.debug("become candidate, leaderId:{}, term:{}, now state:{}", leaderId, term, getState());
        }
        if (STATE.compareAndSet(null, RaftStateEnum.CANDIDATE)
                || STATE.compareAndSet(RaftStateEnum.FOLLOWER, RaftStateEnum.CANDIDATE)) {
            bus.publish(RaftConstant.RAFT_STATE_TOPIC, RaftStateEnum.CANDIDATE);
            return;
        }
        log.error("become candidate fail, now state:{}", getState());
    }

    public void becomeFollower(int leaderId, int term) {
        if (log.isDebugEnabled()) {
            log.debug("become follower,leaderId:{}, term:{}, now state:{}", leaderId, term, getState());
        }
        if (STATE.get() == RaftStateEnum.FOLLOWER) {
            // Update leader and term
            setLeaderId(leaderId);
            this.term.set(term);
            return;
        }
        if (STATE.compareAndSet(RaftStateEnum.CANDIDATE, RaftStateEnum.FOLLOWER)
                || STATE.compareAndSet(RaftStateEnum.LEADER, RaftStateEnum.FOLLOWER)) {
            setLeaderId(leaderId);
            this.term.set(term);
            bus.publish(RaftConstant.RAFT_STATE_TOPIC, RaftStateEnum.FOLLOWER);
            return;
        }
        log.error("become follower fail, now state:{}", getState());
    }

    public void becomeLeader() {
        if (log.isDebugEnabled()) {
            log.debug("become leader,leaderId:{}, term:{}, now state:{}", leaderId, term, getState());
        }
        if (STATE.compareAndSet(RaftStateEnum.CANDIDATE, RaftStateEnum.LEADER)
                || STATE.compareAndSet(RaftStateEnum.FOLLOWER, RaftStateEnum.LEADER)) {
            setLeaderId(RaftUtils.getIdByHost(null));
            this.term.addAndGet(1);
            bus.publish(RaftConstant.RAFT_STATE_TOPIC, RaftStateEnum.LEADER);
            return;
        }
        log.error("become Leader fail, now state:{}", getState());
    }

    /**
     * get raft status
     * @return status enum
     */
    public RaftStateEnum getState() {
        return STATE.get();
    }

    /**
     * reset all state
     * just for test
     */
    public void reset() {
        STATE.set(null);
        lastLeaderHeartbeatTime = 0;
        leaderId = -1;
        term.set(0);
    }
}
