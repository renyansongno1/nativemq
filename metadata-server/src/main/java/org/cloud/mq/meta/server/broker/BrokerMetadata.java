package org.cloud.mq.meta.server.broker;

import com.google.gson.Gson;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.cloud.mq.meta.api.BrokerDeleteRequest;
import org.cloud.mq.meta.api.BrokerRegisterRequest;
import org.cloud.mq.meta.api.BrokerUpdateRequest;
import org.cloud.mq.meta.server.common.MetadataDefinition;
import org.cloud.mq.meta.server.common.MetadataTypeEnum;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * broker metadata handler
 * @author renyansong
 */
@ApplicationScoped
@Slf4j
public class BrokerMetadata {

    /**
     * broker data map
     * key: cluster name
     * value: broker list in cluster
     */
    private static final Map<String, Set<BrokerItem>> BROKER_MAP = new ConcurrentHashMap<>(1024);

    private static final Gson GSON = new Gson();

    /**
     * add broker Metadata
     * @param metadataDefinition Metadata define
     */
    @SuppressWarnings({"ReassignedVariable", "SynchronizationOnLocalVariableOrMethodParameter"})
    public void putBroker(MetadataDefinition metadataDefinition) {
        if (metadataDefinition.getMetadataTypeEnum() != MetadataTypeEnum.BROKER) {
            throw new BrokerMetadataException("not broker metadata");
        }
        switch (metadataDefinition.getMetadataOperateEnum()) {
            case ADD -> {
                String dataJson = metadataDefinition.getDataJson();
                BrokerRegisterRequest brokerRegisterRequest = GSON.fromJson(dataJson, BrokerRegisterRequest.class);
                String cluster = brokerRegisterRequest.getCluster();
                Set<BrokerItem> brokerItems = BROKER_MAP.get(cluster);
                if (brokerItems == null) {
                    synchronized (this) {
                        brokerItems = BROKER_MAP.get(cluster);
                        if (brokerItems == null) {
                            brokerItems = new HashSet<>();
                            BrokerItem item = BrokerItem.builder()
                                    .cpu(brokerRegisterRequest.getCpu())
                                    .id(brokerRegisterRequest.getId())
                                    .ip(brokerRegisterRequest.getIp())
                                    .domain(brokerRegisterRequest.getDomain())
                                    .memory(brokerRegisterRequest.getMemory())
                                    .status(brokerRegisterRequest.getStatus())
                                    .name(brokerRegisterRequest.getName())
                                    .build();
                            brokerItems.add(item);
                            BROKER_MAP.put(cluster, brokerItems);
                        } else {
                            synchronized (brokerItems) {
                                BrokerItem item = BrokerItem.builder()
                                        .cpu(brokerRegisterRequest.getCpu())
                                        .id(brokerRegisterRequest.getId())
                                        .ip(brokerRegisterRequest.getIp())
                                        .domain(brokerRegisterRequest.getDomain())
                                        .memory(brokerRegisterRequest.getMemory())
                                        .status(brokerRegisterRequest.getStatus())
                                        .name(brokerRegisterRequest.getName())
                                        .build();
                                brokerItems.add(item);
                                BROKER_MAP.put(cluster, brokerItems);
                            }
                        }
                    }
                }
            }
            case UPDATE -> {
                String dataJson = metadataDefinition.getDataJson();
                BrokerUpdateRequest brokerUpdateRequest = GSON.fromJson(dataJson, BrokerUpdateRequest.class);
                String cluster = brokerUpdateRequest.getCluster();
                Set<BrokerItem> brokerItems = BROKER_MAP.get(cluster);
                if (brokerItems == null) {
                    throw new BrokerMetadataException("cluster is not found");
                }
                Iterator<BrokerItem> iterator = brokerItems.iterator();
                while (iterator.hasNext()) {
                    BrokerItem brokerItem = iterator.next();
                    if (brokerItem.getId().equals(brokerUpdateRequest.getId())) {
                        iterator.remove();
                        BrokerItem item = BrokerItem.builder()
                                .cpu(brokerUpdateRequest.getCpu())
                                .id(brokerUpdateRequest.getId())
                                .ip(brokerUpdateRequest.getIp())
                                .domain(brokerUpdateRequest.getDomain())
                                .memory(brokerUpdateRequest.getMemory())
                                .status(brokerUpdateRequest.getStatus())
                                .name(brokerUpdateRequest.getName())
                                .build();
                        brokerItems.add(item);
                        break;
                    }
                }
            }
            case DELETE -> {
                String dataJson = metadataDefinition.getDataJson();
                BrokerDeleteRequest brokerDeleteRequest = GSON.fromJson(dataJson, BrokerDeleteRequest.class);
                String cluster = brokerDeleteRequest.getCluster();
                Set<BrokerItem> brokerItems = BROKER_MAP.get(cluster);
                if (brokerItems == null) {
                    throw new BrokerMetadataException("cluster is not found");
                }
                Iterator<BrokerItem> iterator = brokerItems.iterator();
                while (iterator.hasNext()) {
                    BrokerItem brokerItem = iterator.next();
                    if (brokerItem.getId().equals(brokerDeleteRequest.getId())) {
                        iterator.remove();
                        break;
                    }
                }
            }
            case null, default -> throw new BrokerMetadataException("not support operate");
        }

    }

    /**
     * broker item
     */
    @Getter
    @Setter
    @ToString
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode(exclude = {"ip"})
    public static class BrokerItem {

        /**
         * broker ID
         */
        private String id;

        /**
         * broker name
         */
        private String name;

        /**
         * broker ip
         */
        private String ip;

        /**
         * broker domain
         */
        private String domain;

        /**
         * broker cpu
         */
        private String cpu;

        /**
         * broker memory
         */
        private String memory;

        /**
         * broker status
         */
        private String status;
    }

}
