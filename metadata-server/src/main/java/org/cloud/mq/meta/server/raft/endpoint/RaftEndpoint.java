package org.cloud.mq.meta.server.raft.endpoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ElectComponent.class, new ElectComponent())
            .create();

    @Inject
    ElectComponent electComponent;

    @GET
    public String getElectStatus() {
        if (log.isDebugEnabled()) {
            log.debug("req raft status, component is :{}", electComponent);
        }
        return GSON.toJson(electComponent);
    }

}
