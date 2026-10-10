package com.chenruichao.service.impl;

import cn.hutool.json.JSONUtil;
import com.chenruichao.entity.ShopType;
import com.chenruichao.mapper.ShopTypeMapper;
import com.chenruichao.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chenruichao.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        List<String> shopList = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (shopList != null && !shopList.isEmpty()) {
            List<ShopType> res = new ArrayList<>();
            for (String shop : shopList) {
                res.add(JSONUtil.toBean(shop, ShopType.class));
            }
            return res;
        }
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        shopList = new ArrayList<>();
        for (ShopType shopType : shopTypeList) {
            shopList.add(JSONUtil.toJsonStr(shopType));
        }
        stringRedisTemplate.opsForList().rightPushAll(key, shopList);
        return shopTypeList;
    }
}
