package org.example.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.redis")
public record AppRedisProperties(
        Socket socket,
        Cluster cluster,
        Connection connection
) {
    public AppRedisProperties {
        socket = socket == null ? new Socket(null) : socket;
        cluster = cluster == null ? new Cluster(null) : cluster;
        connection = connection == null ? new Connection(null) : connection;
    }

    public record Socket(Boolean keepAlive) {
        public Socket {
            keepAlive = keepAlive == null || keepAlive;
        }
    }

    public record Cluster(Refresh refresh) {
        public Cluster {
            refresh = refresh == null ? new Refresh(null, null, null) : refresh;
        }

        public record Refresh(
                Boolean adaptive,
                Boolean dynamicRefreshSources,
                Duration period
        ) {
            public Refresh {
                adaptive = adaptive == null || adaptive;
                dynamicRefreshSources = dynamicRefreshSources != null && dynamicRefreshSources;
                period = period == null ? Duration.ofSeconds(60) : period;
            }
        }
    }

    public record Connection(Boolean validate) {
        public Connection {
            validate = validate != null && validate;
        }
    }
}
