package org.cloud.mq.meta.server.raft;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.cloud.mq.meta.raft.*;
import org.cloud.mq.meta.server.raft.election.ElectComponent;

/**
 * grpc raft impl
 * @author renyansong
 */
@GrpcService
public class RaftServerImpl implements RaftServerService {

    @Inject
    ElectComponent electComponent;

    @Override
    public Uni<RaftVoteRes> requestVote(RaftVoteReq request) {
        return Uni.createFrom().item(electComponent.receiveVote(request));
    }

    @Override
    public Uni<ReadIndexRes> readIndex(ReadIndexReq request) {
        return Uni.createFrom().item(electComponent.readIndex(request));
    }

    @Override
    public Multi<AppendLogRes> appendEntries(Multi<AppendLogReq> request) {
        return electComponent.appendEntry(request);
    }

}
