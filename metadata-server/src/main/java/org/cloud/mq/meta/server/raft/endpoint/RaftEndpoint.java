package org.cloud.mq.meta.server.raft.endpoint;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.server.raft.election.ElectComponent;

/**
 * endpoint
 * @author renyansong
 */
@Path("raft")
@Slf4j
public class RaftEndpoint {

    @Inject
    ElectComponent electComponent;

    /**
     * status
     * @return res
     */
    @GET
    public Uni<ElectComponent> getElectStatus() {
        if (log.isDebugEnabled()) {
            log.debug("req raft status, component is :{}", electComponent);
        }
        return Uni.createFrom().item(electComponent);
    }

}
