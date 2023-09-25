package org.cloud.mq.broker;

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.util.StringUtil;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.broker.conf.BrokerConfig;
import org.cloud.mq.broker.constant.BrokerConstant;
import org.cloud.mq.meta.api.BrokerRegisterRequest;
import org.cloud.mq.meta.client.MetaBrokerClient;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.UUID;

/**
 * start
 * @author renyansong
 */
@ApplicationScoped
@Startup
@Slf4j
public class BrokerStart {

    @Inject
    MetaBrokerClient metaBrokerClient;

    @Inject
    BrokerConfig brokerConfig;

    @PostConstruct
    public void init() {
        initConfig();

        log.info("broker started, now registry to meta server...");
        BrokerRegisterRequest registerRequest = null;
        try {
            registerRequest = BrokerRegisterRequest.newBuilder()
                    .setId(UUID.randomUUID().toString())
                    .setCluster(brokerConfig.getCluster())
                    .setCpu(String.valueOf(Runtime.getRuntime().availableProcessors()))
                    .setMemory(Runtime.getRuntime().totalMemory() / 1024 / 1024 +"M")
                    .setIp(InetAddress.getLocalHost().getHostAddress())
                    .setDomain(brokerConfig.getDomain())
                    .build();
        } catch (UnknownHostException e) {
            log.error("UnknownHostException", e);
            System.exit(-1);
        }
        Boolean registryRes = metaBrokerClient.registerBroker(registerRequest);
        if (!registryRes) {
            log.error("broker registry fail, now exit....");
            System.exit(-1);
        }
        log.info("broker registry success");
    }

    /**
     * init config from env
     */
    private void initConfig() {
        String home = System.getenv(BrokerConstant.NATIVE_MQ_ENV);
        if (!StringUtil.isNullOrEmpty(home)) {
            Properties properties = new Properties();
            try (FileInputStream fileInputStream = new FileInputStream(home + BrokerConstant.CONF_NAME)) {
                properties.load(fileInputStream);

                String cluster = properties.getProperty("cluster");
                brokerConfig.setCluster(cluster);
            } catch (IOException e) {
               log.error("load conf error", e);
               System.exit(-1);
            }
        }
    }
}
