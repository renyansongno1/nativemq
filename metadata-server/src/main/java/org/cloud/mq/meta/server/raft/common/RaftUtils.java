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
            String[] hostnames = hostname.split("-");
            return Integer.parseInt(hostnames[hostnames.length - 1]);
        }
        String[] hostnames = host.split("-");
        return Integer.parseInt(hostnames[hostnames.length - 1]);
    }

}
