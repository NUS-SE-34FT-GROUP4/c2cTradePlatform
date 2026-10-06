package sg.edu.nus.iss.c2csectrade.scheduled;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import sg.edu.nus.iss.c2csectrade.service.OrderService;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sprint 3 acceptance checks for the scheduled release of unpaid stock. */
@ExtendWith(MockitoExtension.class)
class OrderExpiryJobTest {

    @Mock private OrderService orderService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks private OrderExpiryJob job;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(job, "lockSeconds", 60L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("Holding the lock, the job sweeps and then releases it")
    void sweepsWhenLockAcquired() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(orderService.expireOverdueOrders()).thenReturn(2);

        job.run();

        verify(orderService).expireOverdueOrders();
        verify(redisTemplate).delete("lock:order-expiry");
    }

    @Test
    @DisplayName("Another instance holds the lock, so this one does nothing")
    void skipsWhenLockHeld() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);

        job.run();

        verifyNoInteractions(orderService);
        // The lock belongs to the other instance; clearing it would let a third
        // run in while that one is still working.
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("A failing sweep still releases the lock, so the next tick can run")
    void releasesLockOnFailure() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(orderService.expireOverdueOrders()).thenThrow(new RuntimeException("database down"));

        job.run();

        verify(redisTemplate).delete("lock:order-expiry");
    }

    @Test
    @DisplayName("Without Redis the job still runs, because a single instance still needs it")
    void runsWhenRedisUnavailable() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new RuntimeException("connection refused"));

        job.run();

        verify(orderService).expireOverdueOrders();
    }
}
