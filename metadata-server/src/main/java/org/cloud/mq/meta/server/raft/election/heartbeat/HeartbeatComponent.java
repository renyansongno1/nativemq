package org.cloud.mq.meta.server.raft.election.heartbeat;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.raft.AppendLogReq;
import org.cloud.mq.meta.server.raft.client.RaftClient;

import java.time.Duration;

/**
 * Heartbeat control
 * @author renyansong
 */
@ApplicationScoped
@Slf4j
public class HeartbeatComponent {

    @Inject
    RaftClient raftClient;

    @Inject
    HeartbeatStreamObserver heartbeatStreamObserver;

    public void heartbeat(AppendLogReq appendLogReq) {
        for (ManagedChannel managedChannel : raftClient.getAllChannel()) {
            send(managedChannel, appendLogReq);
        }
    }

    private void send(ManagedChannel managedChannel, AppendLogReq appendLogReq) {
        Thread.ofVirtual().start(() -> {
            try {
                StreamObserver<AppendLogReq> appendLogReqStreamObserver = raftClient.appendLog(managedChannel, heartbeatStreamObserver);
                // send msg
                appendLogReqStreamObserver.onNext(appendLogReq);

                appendLogReqStreamObserver.onCompleted();
            } catch (Exception e) {
                log.error("send heartbeat error for channel:{}", managedChannel, e);
            }
        });
    }

}
