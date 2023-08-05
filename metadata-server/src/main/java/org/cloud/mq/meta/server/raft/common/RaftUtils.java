package org.cloud.mq.meta.server.raft.common;

import io.quarkus.runtime.util.StringUtil;

public class RaftUtils {

    private RaftUtils() {}

    public static int getIdByHost(String host) {
        if (StringUtil.isNullOrEmpty(host)) {
            String[] hostnames = System.getenv("HOSTNAME").split("-");
            return Integer.parseInt(hostnames[hostnames.length - 1]);
        }
        String[] hostnames = host.split("-");
        return Integer.parseInt(hostnames[hostnames.length - 1]);
    }

}
