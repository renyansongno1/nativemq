package org.cloud.mq.meta.server.raft;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.cloud.mq.meta.raft.*;
import org.cloud.mq.meta.server.raft.election.RaftComponent;

/**
 * grpc raft impl
 * @author renyansong
 */
@GrpcService
public class RaftServerImpl implements RaftServerService {

    @Inject
    RaftComponent raftComponent;

    @Override
    public Uni<RaftVoteRes> requestVote(RaftVoteReq request) {
        return Uni.createFrom().item(raftComponent.receiveVote(request));
    }

    @Override
    public Uni<ReadIndexRes> readIndex(ReadIndexReq request) {
        return Uni.createFrom().item(raftComponent.readIndex(request));
    }

    @Override
    public Multi<AppendLogRes> appendEntries(Multi<AppendLogReq> request) {
        return request.onItem().transform(item -> raftComponent.appendEntry(item));
    }

}
