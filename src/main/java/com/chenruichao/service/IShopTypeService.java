package com.chenruichao.service;

import com.chenruichao.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IShopTypeService extends IService<ShopType> {

    List<ShopType> queryTypeList();
}
