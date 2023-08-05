package org.cloud.mq.control.panel.pojo;


/**
 * broker data type
 */
public class BrokerTO implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String cluster;
    private String ip;
    private String domain;
    private String cpu;
    private String memory;
    private BrokerStatusTO status;

    public BrokerTO() {
    }

    public BrokerTO(String id, String name, String cluster, String ip, String domain, String cpu, String memory, BrokerStatusTO status) {
        this.id = id;
        this.name = name;
        this.cluster = cluster;
        this.ip = ip;
        this.domain = domain;
        this.cpu = cpu;
        this.memory = memory;
        this.status = status;
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
    public void setCluster(String Cluster) {
        this.cluster = Cluster;
    }

    public String getIp() {
        return ip;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getDomain() {
        return domain;
    }
    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getCpu() {
        return cpu;
    }
    public void setCpu(String cpu) {
        this.cpu = cpu;
    }

    public String getMemory() {
        return memory;
    }
    public void setMemory(String memory) {
        this.memory = memory;
    }

    public BrokerStatusTO getStatus() {
        return status;
    }
    public void setStatus(BrokerStatusTO status) {
        this.status = status;
    }



    public static Builder builder() {
        return new Builder();
    }

    @javax.annotation.processing.Generated(
        value = "com.kobylynskyi.graphql.codegen.GraphQLCodegen",
        date = "2023-07-14T15:46:14+0800"
    )
    public static class Builder {

        private String id;
        private String name;
        private String Cluster;
        private String ip;
        private String domain;
        private String cpu;
        private String memory;
        private BrokerStatusTO status;
        private java.util.Map<String, Object> additionalFields;

        public Builder() {
        }

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setCluster(String Cluster) {
            this.Cluster = Cluster;
            return this;
        }

        public Builder setIp(String ip) {
            this.ip = ip;
            return this;
        }

        public Builder setDomain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder setCpu(String cpu) {
            this.cpu = cpu;
            return this;
        }

        public Builder setMemory(String memory) {
            this.memory = memory;
            return this;
        }

        public Builder setStatus(BrokerStatusTO status) {
            this.status = status;
            return this;
        }

        public Builder setAdditionalFields(java.util.Map<String, Object> additionalFields) {
            this.additionalFields = additionalFields;
            return this;
        }


        public BrokerTO build() {
            return new BrokerTO(id, name, Cluster, ip, domain, cpu, memory, status);
        }

    }
}
