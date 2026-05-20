package infra;

import config.TestBootApplication;
import config.TestRedisConfig;
import org.example.common.testcontainer.RedisTestContainerInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ContextConfiguration;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@DataRedisTest(properties = {"spring.data.redis.repositories.enabled=false"})
@ContextConfiguration(
        classes = {TestBootApplication.class, TestRedisConfig.class},
        initializers = RedisTestContainerInitializer.class
)
class RedisClusterTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private ValueOperations<String, String> stringOps;

    private final int KEY_COUNT = 1000;
    private final String KEY_PREFIX = "key_";

    private final Map<String, Integer> map = new ConcurrentHashMap<>();
    private final Random random = new Random();

    @BeforeEach
    void setUp() {
        stringOps = redisTemplate.opsForValue();

        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();

        map.clear();
    }

    @Test
    @Disabled
    void consistencyTest() throws ExecutionException, InterruptedException {
        AtomicLong readCount = new AtomicLong();
        AtomicLong writeCount = new AtomicLong();
        AtomicLong lostCount = new AtomicLong();

        ExecutorService executorService = Executors.newSingleThreadExecutor();

        Runnable task = () -> {
            long start = System.currentTimeMillis();

            while (System.currentTimeMillis() - start < 3000) {
                try {
                    String readKey = KEY_PREFIX + random.nextInt(KEY_COUNT);

                    String redisVal = stringOps.get(readKey);
                    int actual = redisVal != null ? Integer.parseInt(redisVal) : 0;
                    int expected = map.getOrDefault(readKey, 0);

                    if (actual != expected) {
                        lostCount.addAndGet(expected - actual);
                    }

                    readCount.incrementAndGet();

                    String writeKey = KEY_PREFIX + random.nextInt(KEY_COUNT);

                    Long incremented = stringOps.increment(writeKey);
                    map.put(writeKey, incremented == null ? 0 : incremented.intValue());

                    writeCount.incrementAndGet();

                    Thread.sleep(1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        executorService.submit(task).get();
        executorService.shutdown();

        System.out.printf(
                "[TEST DONE] %d R | %d W | %d lost%n",
                readCount.get(),
                writeCount.get(),
                lostCount.get()
        );
    }
}