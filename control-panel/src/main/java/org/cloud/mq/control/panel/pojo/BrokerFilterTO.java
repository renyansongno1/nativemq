package org.cloud.mq.control.panel.pojo;


/**
 * broker filter page input param
 */
public class BrokerFilterTO implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String ip;
    private String status;
    private int pageNo;
    private int pageSize;

    public BrokerFilterTO() {
    }

    public BrokerFilterTO(String name, String ip, String status, int pageNo, int pageSize) {
        this.name = name;
        this.ip = ip;
        this.status = status;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return ip;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    public int getPageNo() {
        return pageNo;
    }
    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }



    public static Builder builder() {
        return new Builder();
    }

    @javax.annotation.processing.Generated(
        value = "com.kobylynskyi.graphql.codegen.GraphQLCodegen",
        date = "2023-07-14T15:46:14+0800"
    )
    public static class Builder {

        private String name;
        private String ip;
        private String status;
        private int pageNo;
        private int pageSize;
        private java.util.Map<String, Object> additionalFields;

        public Builder() {
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setIp(String ip) {
            this.ip = ip;
            return this;
        }

        public Builder setStatus(String status) {
            this.status = status;
            return this;
        }

        public Builder setPageNo(int pageNo) {
            this.pageNo = pageNo;
            return this;
        }

        public Builder setPageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder setAdditionalFields(java.util.Map<String, Object> additionalFields) {
            this.additionalFields = additionalFields;
            return this;
        }


        public BrokerFilterTO build() {
            return new BrokerFilterTO(name, ip, status, pageNo, pageSize);
        }

    }
}
