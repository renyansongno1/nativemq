package org.cloud.mq.meta.server.raft.election.heartbeat;

import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.raft.AppendLogRes;
import org.cloud.mq.meta.server.raft.client.RaftClient;
import org.cloud.mq.meta.server.raft.election.ElectState;
import org.cloud.mq.meta.server.raft.peer.PeerWaterMark;

/**
 * Heartbeat Stream observer
 * @author renyansong
 */
@ApplicationScoped
@Slf4j
public class HeartbeatStreamObserver implements StreamObserver<AppendLogRes> {

    @Inject
    RaftClient raftClient;

    @Inject
    ElectState electState;

    @Inject
    PeerWaterMark peerWaterMark;

    @Override
    public void onNext(AppendLogRes appendLogRes) {
        if (appendLogRes.getResult() == AppendLogRes.AppendResult.NOT_LEADER) {
            if (log.isDebugEnabled()) {
                log.debug("receive higher terms on Heartbeat res:{}", appendLogRes);
            }
            // other leader is higher term
            electState.becomeFollower(appendLogRes.getLeaderId(), appendLogRes.getTerm());
            return;
        }
        if (appendLogRes.getResult() == AppendLogRes.AppendResult.INCONSISTENCY) {
            // TODO: 2023/7/31 log error
        }
        if (appendLogRes.getResult() == AppendLogRes.AppendResult.SUCCESS) {
            if (log.isDebugEnabled()) {
                log.debug("receive:{}, success heartbeat response", appendLogRes.getMyId());
            }
            peerWaterMark.refreshPeerItem(raftClient.getPeerAddrByChannel(raftClient.getChannelById(appendLogRes.getMyId())), appendLogRes.getNextIndex());
        }
    }

    @Override
    public void onError(Throwable throwable) {
        log.error("send heartbeat beat error", throwable);
    }

    @Override
    public void onCompleted() {
        if (log.isDebugEnabled()) {
            log.debug("send heartbeat completed");
        }
    }

}
