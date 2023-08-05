package org.cloud.mq.meta.server.raft.election;

/**
 * raft enum
 * @author renyansong
 */
public enum RaftStateEnum {

    /**
     * candidate
     */
    CANDIDATE,

    /**
     * leader
     */
    LEADER,

    /**
     * follower
     */
    FOLLOWER,

    /**
     * coordinator
     */
    COORDINATOR,

}
