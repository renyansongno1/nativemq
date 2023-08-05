package org.cloud.mq.control.panel.pojo;


/**
 * broker page result
 */
public class BrokerPageTO implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private int pageNo;
    private int pageSize;
    private java.util.List<BrokerTO> brokers;

    public BrokerPageTO() {
    }

    public BrokerPageTO(int pageNo, int pageSize, java.util.List<BrokerTO> brokers) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.brokers = brokers;
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

    public java.util.List<BrokerTO> getBrokers() {
        return brokers;
    }
    public void setBrokers(java.util.List<BrokerTO> brokers) {
        this.brokers = brokers;
    }



    public static Builder builder() {
        return new Builder();
    }

    @javax.annotation.processing.Generated(
        value = "com.kobylynskyi.graphql.codegen.GraphQLCodegen",
        date = "2023-07-14T15:46:14+0800"
    )
    public static class Builder {

        private int pageNo;
        private int pageSize;
        private java.util.List<BrokerTO> brokers;
        private java.util.Map<String, Object> additionalFields;

        public Builder() {
        }

        public Builder setPageNo(int pageNo) {
            this.pageNo = pageNo;
            return this;
        }

        public Builder setPageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder setBrokers(java.util.List<BrokerTO> brokers) {
            this.brokers = brokers;
            return this;
        }

        public Builder setAdditionalFields(java.util.Map<String, Object> additionalFields) {
            this.additionalFields = additionalFields;
            return this;
        }


        public BrokerPageTO build() {
            return new BrokerPageTO(pageNo, pageSize, brokers);
        }

    }
}
