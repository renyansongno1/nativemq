package org.cloud.mq.meta.server.raft.endpoint;

import com.google.gson.Gson;
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

    private static final Gson GSON = new Gson();

    @Inject
    RaftComponent raftComponent;

    /**
     * status
     * @return res
     */
    @GET
    public Uni<String> getElectStatus() {
        if (log.isDebugEnabled()) {
            log.debug("req raft status, component is :{}", raftComponent);
        }
        return Uni.createFrom().item(GSON.toJson(raftComponent));
    }

}
