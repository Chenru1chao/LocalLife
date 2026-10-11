package com.chenruichao;

import com.chenruichao.entity.SeckillVoucher;
import com.chenruichao.entity.Shop;
import com.chenruichao.service.ISeckillVoucherService;
import com.chenruichao.service.impl.ShopServiceImpl;
import com.chenruichao.utils.CacheClient;
import com.chenruichao.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.chenruichao.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.chenruichao.utils.RedisConstants.SECKILL_BEGIN_KEY;
import static com.chenruichao.utils.RedisConstants.SECKILL_END_KEY;
import static com.chenruichao.utils.RedisConstants.SECKILL_STOCK_KEY;
import static com.chenruichao.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class LocalLifeApplicationTests {
    @Autowired
    ShopServiceImpl shopService;

    @Autowired
    CacheClient cacheClient;

    @Autowired
    RedisIdWorker redisIdWorker;

    @Autowired
    ISeckillVoucherService seckillVoucherService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    private ExecutorService executorService = Executors.newFixedThreadPool(300);

    @Test
    public void PreloadHotKey() {
        cacheClient.setWithLogicalExpiration(CACHE_SHOP_KEY + "1", shopService.getById(1), 20L, TimeUnit.SECONDS);
    }

    // 缓存预热：把秒杀券的库存和活动时间刷进redis 供seckill.lua判断
    @Test
    public void preloadSeckillVoucher() {
        for (SeckillVoucher sv : seckillVoucherService.list()) {
            String id = sv.getVoucherId().toString();
            stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + id, sv.getStock().toString());

            cacheActivityTime(SECKILL_BEGIN_KEY + id, sv.getBeginTime());
            cacheActivityTime(SECKILL_END_KEY + id, sv.getEndTime());

            System.out.println("voucherId=" + id + " stock=" + sv.getStock()
                    + " begin=" + sv.getBeginTime() + " end=" + sv.getEndTime()
                    + " now=" + LocalDateTime.now());
        }
    }

    private void cacheActivityTime(String key, LocalDateTime time) {
        if (time == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key, String.valueOf(toEpochSecond(time)));
    }

    private long toEpochSecond(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    @Test
    public void RedisIdWorkerTest() throws InterruptedException {
        CountDownLatch count = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                redisIdWorker.nextId("test");
            }
            count.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(task);
        }
        count.await();
        long end = System.currentTimeMillis();
        System.out.println("totalTime:" + (end - begin));
    }

    @Test
    public void sendShopGeo() {
        Map<Long, List<Shop>> shops = shopService.list().stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> shop : shops.entrySet()) {
            Long type = shop.getKey();
            List<Shop> shopList = shop.getValue();
            String key = SHOP_GEO_KEY + type;
            List<RedisGeoCommands.GeoLocation<String>> geoLocations = new ArrayList<>();
            for (Shop s : shopList) {
                geoLocations.add(new RedisGeoCommands.GeoLocation<>(
                        s.getId().toString(), new Point(s.getX(), s.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, geoLocations);
        }
    }
}
