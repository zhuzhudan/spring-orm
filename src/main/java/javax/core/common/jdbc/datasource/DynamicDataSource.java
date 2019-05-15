package javax.core.common.jdbc.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DynamicDataSource extends AbstractRoutingDataSource {
    // entry的目的：用于给每个数据源打个标记
    private DynamicDataSourceEntry dataSourceEntry;

    protected Object determineCurrentLookupKey() {
        return this.dataSourceEntry.get();
    }

    public DynamicDataSourceEntry getDataSourceEntry() {
        return dataSourceEntry;
    }

    public void setDataSourceEntry(DynamicDataSourceEntry dataSourceEntry) {
        this.dataSourceEntry = dataSourceEntry;
    }
}
