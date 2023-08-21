package org.cloud.mq.meta.server.testing;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.server.raft.log.LogProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

/**
 * log proxy test
 * @author renyansong
 */
@QuarkusTest
@Slf4j
public class LogProxyTest {

    @Inject
    LogProxy logProxy;

    @BeforeEach
    public void clearDb() throws Exception{
        log.info("before each delete all");
        logProxy.clearAll();
    }

    @AfterEach
    public void clear() throws Exception {
        log.info("after each delete all");
        logProxy.clearAll();
    }

    @Test
    public void appendLogTest() {
        log.info("append log test start...");
        String str = "append log";
        logProxy.appendLog(1, str.getBytes(StandardCharsets.UTF_8));

        byte[] readRes = logProxy.readIndex(1);
        Assertions.assertEquals(str, new String(readRes));
    }

}
