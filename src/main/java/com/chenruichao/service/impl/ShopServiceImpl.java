package com.chenruichao.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chenruichao.dto.Result;
import com.chenruichao.entity.Shop;
import com.chenruichao.mapper.ShopMapper;
import com.chenruichao.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chenruichao.utils.CacheClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.chenruichao.utils.RedisConstants.*;
import static com.chenruichao.utils.SystemConstants.DEFAULT_PAGE_SIZE;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    CacheClient cacheClient;

    @Override
    public Result queryShopById(Long id) {
        // 解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, CACHE_SHOP_TTL, TimeUnit.SECONDS,
                (shopId) -> getById(id));

        // 解决缓存击穿
        /*
        Shop shop = cacheClient.queryWithCacheBreakdown(CACHE_SHOP_KEY, LOCK_SHOP_KEY, id,
                Shop.class, CACHE_SHOP_TTL, TimeUnit.SECONDS, new Function<Long, Shop>() {
                    @Override
                    public Shop apply(Long aLong) {
                        return getById(id);
                    }
                });
         */

        if (shop == null) {
            return Result.fail("当前商品不存在!");
        }

        return Result.ok(shop);
    }

    /**
     * 采用互斥锁解决缓存击穿问题
     * @param id
     * @return Result
     * @author chenru1chao
     */
    /*
    @Override
    public Result queryShopById(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        if (StrUtil.isNotBlank(shopJson)) {
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }
        if ("".equals(shopJson)) {
            return Result.fail("当前查询店铺不存在");
        }
        Shop shop;
        boolean isLock = false;
        try {
            if (!tryLock(lockKey)) {
                Thread.sleep(50);
                return queryShopById(id);
            }
            isLock = true;
            shopJson = stringRedisTemplate.opsForValue().get(shopKey);
            if (StrUtil.isNotBlank(shopJson)) {
                return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
            }
            shop = getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("当前查询店铺不存在");
            }
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (isLock) {
                unlock(lockKey);
            }
        }
        return Result.ok(shop);
    }
    */


    /**
     * 采用逻辑过期解决缓存击穿问题
     * @param
     * @return Result
     * @author chenru1chao
     */
    /*
    @Override
    public Result queryShopById(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        String dataJson = stringRedisTemplate.opsForValue().get(shopKey);

        if (StrUtil.isBlank(dataJson)) {
            return Result.fail("当前热门商品并未缓存到redis中");
        }

        RedisData redisdata = JSONUtil.toBean(dataJson, RedisData.class);
        if (redisdata.getExpireTime() == null || redisdata.getData() == null) {
            return Result.fail("缓存数据异常");
        }
        Shop shop = JSONUtil.toBean((JSONObject) redisdata.getData(), Shop.class);

        if (redisdata.getExpireTime().isAfter(LocalDateTime.now())) {
            return Result.ok(shop);
        }

        boolean isLock = tryLock(lockKey);

        if (!isLock) {
            return Result.ok(shop);
        }

        dataJson = stringRedisTemplate.opsForValue().get(shopKey);
        if (StrUtil.isNotBlank(dataJson)) {
            redisdata = JSONUtil.toBean(dataJson, RedisData.class);
            shop = JSONUtil.toBean((JSONObject) redisdata.getData(), Shop.class);
            if (redisdata.getExpireTime().isAfter(LocalDateTime.now())) {
                unlock(lockKey);
                return Result.ok(shop);
            }
        }

        executorService.submit(() -> {
            try {
                saveHotKey(id, 20L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unlock(lockKey);
            }
        });

        return Result.ok(shop);
    }

    public void saveHotKey(Long id, Long expireTime) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    private boolean tryLock(String key) {
        Boolean res = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(res);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }*/

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺的id不能为空!");
        }

        shop.setUpdateTime(LocalDateTime.now());
        updateById(shop);

        stringRedisTemplate.opsForValue().getAndDelete(CACHE_SHOP_KEY + shop.getId().toString());

        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        int begin = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;

        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = stringRedisTemplate.opsForGeo().search(key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );

        if (geoResults == null) {
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> contents = geoResults.getContent();

        if (begin >= contents.size()) {
            return Result.ok(Collections.emptyList());
        }

        ArrayList<Long> ids = new ArrayList<>(contents.size());
        HashMap<Long, Distance> shopDistance = new HashMap<>(contents.size());

        contents.stream().skip(begin).forEach(content -> {
            String shopId = content.getContent().getName();
            ids.add(Long.valueOf(shopId));
            Distance distance = content.getDistance();
            shopDistance.put(Long.valueOf(shopId), distance);
        });

        String idStr = StrUtil.join(",", ids);

        List<Shop> shops = query().in("id", ids).last("order by field(id," + idStr + ")").list();

        for (Shop shop : shops) {
            shop.setDistance(shopDistance.get(shop.getId()).getValue());
        }

        return Result.ok(shops);
    }
}
