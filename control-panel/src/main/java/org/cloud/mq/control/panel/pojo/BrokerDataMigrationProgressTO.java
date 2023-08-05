package org.cloud.mq.control.panel.pojo;


/**
 * when the broker transfer data, this data can be get
 */
public class BrokerDataMigrationProgressTO implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String cluster;
    private int totalMessages;
    private int totalTopics;
    private int migratedMessages;
    private int migratedTopics;

    public BrokerDataMigrationProgressTO() {
    }

    public BrokerDataMigrationProgressTO(String id, String name, String cluster, int totalMessages, int totalTopics, int migratedMessages, int migratedTopics) {
        this.id = id;
        this.name = name;
        this.cluster = cluster;
        this.totalMessages = totalMessages;
        this.totalTopics = totalTopics;
        this.migratedMessages = migratedMessages;
        this.migratedTopics = migratedTopics;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getCluster() {
        return cluster;
    }
    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public int getTotalMessages() {
        return totalMessages;
    }
    public void setTotalMessages(int totalMessages) {
        this.totalMessages = totalMessages;
    }

    public int getTotalTopics() {
        return totalTopics;
    }
    public void setTotalTopics(int totalTopics) {
        this.totalTopics = totalTopics;
    }

    public int getMigratedMessages() {
        return migratedMessages;
    }
    public void setMigratedMessages(int migratedMessages) {
        this.migratedMessages = migratedMessages;
    }

    public int getMigratedTopics() {
        return migratedTopics;
    }
    public void setMigratedTopics(int migratedTopics) {
        this.migratedTopics = migratedTopics;
    }

}
