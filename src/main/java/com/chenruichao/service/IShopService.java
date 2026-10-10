package com.chenruichao.service;

import com.chenruichao.dto.Result;
import com.chenruichao.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IShopService extends IService<Shop> {

    Result updateShop(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);

    Result queryShopById(Long id);
}
