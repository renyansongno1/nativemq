package org.cloud.mq.meta.server.raft.peer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.cloud.mq.meta.server.raft.election.RaftConstant;
import org.cloud.mq.meta.server.raft.log.LogProxy;

import java.time.Duration;

/**
 * log sync water mark
 * @author renyansong
 */
@ApplicationScoped
public class PeerWaterMark {

    @Inject
    LogProxy logProxy;

    private static final Cache<String, PeerItem> WATER_MARK_CACHE = Caffeine.newBuilder()
            .maximumSize(10_000_000)
            .expireAfterWrite(Duration.ofMillis(RaftConstant.HEARTBEAT_INTERVAL_S * 3000))
            .expireAfterAccess(Duration.ofMillis(RaftConstant.HEARTBEAT_INTERVAL_S * 3000))
            .evictionListener(((s, peerItem, removalCause) -> {

            }))
            .build();

    private long highWaterMark;

    public void syncHighWaterMark(long logIndex) {
        this.highWaterMark = logIndex;
    }

    /**
     * when receive heartbeat, update peer item data
     * @param peer peer ip:port
     * @param logIndex now log index
     */
    public void refreshPeerItem(String peer, long logIndex) {
        PeerItem item = WATER_MARK_CACHE.get(peer, k -> null);
        if (item == null) {
            WATER_MARK_CACHE.put(peer, new PeerItem(peer, logIndex));
            return;
        }
        item.setPeer(peer);
        item.setLowWaterMark(logIndex);
    }

    public long getWaterMark(String peer) {
        PeerItem peerItem = WATER_MARK_CACHE.get(peer, k -> null);
        if (peerItem == null) {
            return -1L;
        }
        return peerItem.getLowWaterMark();
    }

    public void updateLowWaterMark(String peer, long lowWaterMark) {
        PeerItem peerItem = WATER_MARK_CACHE.get(peer, k -> null);
        if (peerItem == null) {
            return;
        }
        peerItem.setLowWaterMark(lowWaterMark);
    }

    @Getter
    @Setter
    @ToString
    private static class PeerItem {

        private String peer;

        private long lowWaterMark;

        public PeerItem(String peer, long lowWaterMark) {
            this.peer = peer;
            this.lowWaterMark = lowWaterMark;
        }

    }

}
