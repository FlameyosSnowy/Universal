package io.github.flameyossnow.universal.cassandra;

import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.policies.RetryPolicy;

@SuppressWarnings("unused")
public record CassandraCredentials(
        String node,
        int port,
        String keyspace,
        PoolingOptions poolingOptions,
        RetryPolicy retryPolicy,
        LoadBalancingPolicy loadBalancingPolicy
) {
    public CassandraCredentials(String node) {
        this(node, -1, null, null, null, null);
    }

    public CassandraCredentials(String node, int port) {
        this(node, port, null, null, null, null);
    }

    public CassandraCredentials(String node, int port, String keyspace) {
        this(node, port, keyspace, null, null, null);
    }

    /**
     * Creates default pooling options optimized for performance.
     */
    public static PoolingOptions defaultPoolingOptions() {
        return new PoolingOptions()
                .setConnectionsPerHost(HostDistance.LOCAL, 4, 10)
                .setConnectionsPerHost(HostDistance.REMOTE, 2, 4)
                .setMaxRequestsPerConnection(HostDistance.LOCAL, 32768)
                .setMaxRequestsPerConnection(HostDistance.REMOTE, 2000);
    }
}
