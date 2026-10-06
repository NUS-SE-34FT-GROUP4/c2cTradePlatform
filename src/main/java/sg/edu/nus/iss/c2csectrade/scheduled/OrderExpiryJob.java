package sg.edu.nus.iss.c2csectrade.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sg.edu.nus.iss.c2csectrade.service.OrderService;

import java.time.Duration;

/**
 * Releases the stock held by orders whose payment window has passed.
 *
 * The work itself already existed and is tested; this only decides when it
 * runs. Two things it has to get right:
 *
 *  - it must be safe to run repeatedly, which it is because the query only
 *    returns orders still in PENDING_PAYMENT past their expiry, and expiring
 *    one moves it out of that set
 *  - it must not run twice at once. Staging runs a single instance today, but
 *    the proposal's architecture allows more, and two instances expiring the
 *    same order would release its reservation twice and inflate the stock. A
 *    Redis key held for the length of the run is what stops that.
 */
@Component
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);
    private static final String LOCK_KEY = "lock:order-expiry";

    private final OrderService orderService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${order.expiry-job.lock-seconds:60}")
    private long lockSeconds;

    public OrderExpiryJob(OrderService orderService, RedisTemplate<String, Object> redisTemplate) {
        this.orderService = orderService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelayString = "${order.expiry-job.interval-ms:60000}")
    public void run() {
        if (!acquireLock()) {
            return;
        }
        try {
            int expired = orderService.expireOverdueOrders();
            if (expired > 0) {
                log.info("Expiry job released the stock held by {} unpaid orders", expired);
            }
        } catch (Exception e) {
            // A scheduled method that throws is simply not retried until the
            // next tick, and the exception would otherwise go unlogged.
            log.error("Expiry job failed; the next run will pick up the same orders", e);
        } finally {
            releaseLock();
        }
    }

    /**
     * The key carries a TTL, so an instance that dies mid-run does not leave
     * the job blocked for good.
     */
    private boolean acquireLock() {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, "1", Duration.ofSeconds(lockSeconds)));
        } catch (Exception e) {
            // Without Redis there is no coordination, but a single instance
            // still needs the job to run, so fall through rather than stop.
            log.warn("Could not reach Redis for the expiry lock, running anyway: {}", e.getMessage());
            return true;
        }
    }

    private void releaseLock() {
        try {
            redisTemplate.delete(LOCK_KEY);
        } catch (Exception e) {
            log.debug("Could not clear the expiry lock; it will expire on its own");
        }
    }
}
