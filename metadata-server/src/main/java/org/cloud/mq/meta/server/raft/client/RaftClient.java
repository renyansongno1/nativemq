package org.cloud.mq.meta.server.raft.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.cloud.mq.meta.server.raft.common.RaftUtils;
import org.cloud.mq.meta.server.raft.peer.PeerFinder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * raft client
 * @author renyansong
 */
@ApplicationScoped
public class RaftClient {

    @ConfigProperty(name = "quarkus.grpc.server.port")
    Integer serverPort;

    @Inject
    PeerFinder peerFinder;

    /**
     * all raft peer grpc channel
     */
    private final List<ManagedChannel> channels = new ArrayList<>();

    private final Map<ManagedChannel, String> mapping = new ConcurrentHashMap<>();

    private final Map<Integer, ManagedChannel> idChannelMapping = new ConcurrentHashMap<>();

    private final Set<String> cachedPeer = new HashSet<>();

    @Scheduled(every="5s")
    public void refreshChannel() {
        for (String peer : peerFinder.getOtherPeer()) {
            if (cachedPeer.contains(peer)) {
                continue;
            }
            // init channel
            ManagedChannel channel = ManagedChannelBuilder.forAddress(peer, serverPort)
                    .usePlaintext()
                    .build();
            int id = RaftUtils.getIdByHost(null);
            idChannelMapping.put(id, channel);
            channels.add(channel);
            mapping.put(channel, peer);
            cachedPeer.add(peer);
        }
    }

    /**
     * get channel
     * @return channel list
     */
    public List<ManagedChannel> getAllChannel() {
        return channels;
    }

    /**
     * get peer hosts by channel
     * @param channel channel
     * @return peer host
     */
    public String getPeerAddrByChannel(ManagedChannel channel) {
        return mapping.get(channel);
    }

    /**
     * get channel by id
     * @param id id
     * @return channel
     */
    public ManagedChannel getChannelById(Integer id) {
        return idChannelMapping.get(id);
    }

}
