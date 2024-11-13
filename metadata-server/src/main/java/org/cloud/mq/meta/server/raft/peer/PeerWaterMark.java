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
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * log sync water mark
 * @author renyansong
 */
@ApplicationScoped
public class PeerWaterMark {

    @Inject
    LogProxy logProxy;

    private static final Lock WAIT_LOCK = new ReentrantLock();

    private static final Condition WAIT_WATERMARK_CONDITION = WAIT_LOCK.newCondition();

    private static final Cache<String, PeerItem> WATER_MARK_CACHE = Caffeine.newBuilder()
            .maximumSize(10_000_000)
            .expireAfterWrite(Duration.ofMillis(RaftConstant.HEARTBEAT_INTERVAL_S * 3000))
            .expireAfterAccess(Duration.ofMillis(RaftConstant.HEARTBEAT_INTERVAL_S * 3000))
            .evictionListener(((s, peerItem, removalCause) -> {

            }))
            .build();

    @Getter
    private long highWaterMark;

    public void syncHighWaterMark(long logIndex) {
        this.highWaterMark = logIndex;
    }

    /**
     * get all peer map
     */
    public Map<String, PeerItem> getPeerMap() {
        return WATER_MARK_CACHE.asMap();
    }

    /**
     * when receive heartbeat, update peer item data
     * @param peer peer ip:port
     */
    public void refreshPeerItem(String peer) {
        PeerItem item = WATER_MARK_CACHE.get(peer, k -> null);
        if (item == null) {
            WATER_MARK_CACHE.put(peer, new PeerItem(peer, 0));
        }
    }

    /**
     * wait for Quorum by provide watermark
     * will block thread
     * @param waterMark wait for
     * @return success
     */
    public boolean waitQuorum(long waterMark) {
        WAIT_LOCK.lock();
        try {
            while (true) {
                // check
                ConcurrentMap<String, PeerItem> peerMap = WATER_MARK_CACHE.asMap();
                int receivedCount = 0;
                for (String peer : peerMap.keySet()) {
                    PeerItem peerItem = WATER_MARK_CACHE.get(peer, k -> null);
                    if (peerItem != null && peerItem.getLowWaterMark() >= waterMark) {
                        receivedCount++;
                    }
                }
                if (receivedCount < peerMap.size() / 2) {
                    try {
                        boolean await = WAIT_WATERMARK_CONDITION.await(3000L, TimeUnit.SECONDS);
                        if (!await) {
                            // timeout
                            return false;
                        }
                    } catch (InterruptedException e) {
                        // ignore
                        return false;
                    }
                } else {
                    return true;
                }
            }
        } finally {
            WAIT_LOCK.unlock();
        }
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
        WAIT_LOCK.lock();
        try {
            WAIT_WATERMARK_CONDITION.signalAll();
        } finally {
            WAIT_LOCK.unlock();
        }
    }

    @Getter
    @Setter
    @ToString
    public static class PeerItem {

        private String peer;

        private long lowWaterMark;

        public PeerItem(String peer, long lowWaterMark) {
            this.peer = peer;
            this.lowWaterMark = lowWaterMark;
        }

    }

}
