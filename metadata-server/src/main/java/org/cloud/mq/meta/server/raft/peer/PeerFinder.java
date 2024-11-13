package org.cloud.mq.meta.server.raft.peer;

import com.google.common.collect.Lists;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cloud.mq.meta.server.raft.common.RaftUtils;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.Attributes;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * raft peer auto find
 * @author renyansong
 */
@ApplicationScoped
@Slf4j
public class PeerFinder {

    private static final CopyOnWriteArraySet<String> PEER_HOST_SET = new CopyOnWriteArraySet<>();

    private static final String DOMAIN = "metadata-server.nativemq.svc.cluster.local";

    @Scheduled(every="10s")
    public void refreshPeer() {
        findPeer();
    }

    public void findPeer() {
        if (log.isDebugEnabled()) {
            log.debug("find SRV:{} all domain service, current thread:{}", DOMAIN, Thread.currentThread().getName());
        }
        try {
            Hashtable<String, String> env = new Hashtable<>(2, 1);
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            InitialDirContext context = new InitialDirContext(env);

            // Specify the type of DNS records to query (in this case, SRV records)
            Attributes attributes = context.getAttributes(DOMAIN, new String[] { "SRV" });

            // Retrieve the SRV records
            javax.naming.directory.Attribute srvRecords = attributes.get("SRV");


            // Iterate over the SRV records and extract the domain names
            if (srvRecords != null) {
                for (int i = 0; i < srvRecords.size(); i++) {
                    String srvRecord = (String) srvRecords.get(i);
                    String[] parts = srvRecord.split(" ");

                    // Extract the domain name from the SRV record (assuming it's at index 3 in the split array)
                    String oneDomain = parts[3];
                    oneDomain =  StringUtils.removeEnd(oneDomain, ".");
                    PEER_HOST_SET.add(oneDomain);
                }
            }
        } catch (javax.naming.NameNotFoundException e) {
            // ignore
            log.warn("ignore name not found exception for:{}", e.getMessage());
        } catch (NamingException e) {
            log.error("naming error", e);
        }
        if (log.isDebugEnabled()) {
            log.debug("all peer domain:{}", Arrays.toString(PEER_HOST_SET.toArray()));
        }
    }

    /**
     * get all peer except myself
     * @return peer list
     */
    public List<String> getOtherPeer() {
        String hostname = RaftUtils.getMyHostName();
        ArrayList<String> list = Lists.newArrayList(PEER_HOST_SET);
        list.removeIf(s -> s.startsWith(hostname));
        return list;
    }

}
