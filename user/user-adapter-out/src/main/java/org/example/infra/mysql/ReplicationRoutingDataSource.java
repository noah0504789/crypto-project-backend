package org.example.infra.mysql;

import org.example.infra.enums.DataSourceType;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class ReplicationRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.isRead()
                ? DataSourceType.READ
                : DataSourceType.WRITE;
    }
}
