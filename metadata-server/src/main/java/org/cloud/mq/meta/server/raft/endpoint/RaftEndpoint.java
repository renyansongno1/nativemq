package org.cloud.mq.meta.server.raft.endpoint;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.server.raft.election.RaftComponent;

/**
 * endpoint
 * @author renyansong
 */
@Path("raft")
@Slf4j
public class RaftEndpoint {

    @Inject
    RaftComponent raftComponent;

    /**
     * status
     * @return res
     */
    @GET
    public Uni<RaftComponent> getElectStatus() {
        if (log.isDebugEnabled()) {
            log.debug("req raft status, component is :{}", raftComponent);
        }
        return Uni.createFrom().item(raftComponent);
    }

}
