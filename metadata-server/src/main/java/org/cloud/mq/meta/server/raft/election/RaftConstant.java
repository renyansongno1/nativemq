package org.cloud.mq.meta.server.raft.election;

/**
 * constant for raft
 * @author renyansong
 */
public class RaftConstant {
    /**
     * vote min interval
     */
    public static final int MIN_VOTE_TIME_INTERVAL_MS = 300;

    /**
     * vote max interval
     */
    public static final int MAX_VOTE_TIME_INTERVAL_MS = 1500;

    /**
     * leader Heartbeat interval
     */
    public static final int HEARTBEAT_INTERVAL_S = 5;

    /**
     * heartbeat scheduler every HEARTBEAT_INTERVAL_MS
     */
    public static final String HEARTBEAT_SCHEDULER = "heartbeat_scheduler";

    /**
     * follower listen leader heartbeat
     */
    public static final String HEARTBEAT_LISTEN_SCHEDULER = "heartbeat_listen_scheduler";

    /**
     * raft state change bus topic
     */
    public static final String RAFT_STATE_TOPIC = "RAFT_STATE_TOPIC";
}
