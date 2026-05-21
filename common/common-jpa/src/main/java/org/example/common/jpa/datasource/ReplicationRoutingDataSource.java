package org.example.common.jpa.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class ReplicationRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.isRead()
                ? DataSourceType.READ
                : DataSourceType.WRITE;
    }
}
