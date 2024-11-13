package org.cloud.mq.meta.server.raft.common;

import io.quarkus.runtime.util.StringUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RaftUtils {

    private RaftUtils() {}

    public static int getIdByHost(String host) {
        if (StringUtil.isNullOrEmpty(host)) {
            String hostname = System.getenv("HOSTNAME");
            if (StringUtil.isNullOrEmpty(hostname)) {
                return 0;
            }
            host = hostname;
        }
        String[] hostnames = host.split("\\.");
        String[] split = hostnames[0].split("-");
        return Integer.parseInt(split[split.length - 1]);
    }

    public static String getMyHostName() {
        String hostname = System.getenv("HOSTNAME");
        if (StringUtil.isNullOrEmpty(hostname)) {
            return "UNKNOWN";
        }
        return hostname;
    }

    /**
     * get short host name
     * @param host full host name
     * @return short host name
     */
    public static String getShortHostName(String host) {
        return host.split("\\.")[0];
    }

}
