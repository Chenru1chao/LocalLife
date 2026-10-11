package com.chenruichao.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chenruichao.dto.Result;
import com.chenruichao.entity.Voucher;
import com.chenruichao.mapper.VoucherMapper;
import com.chenruichao.entity.SeckillVoucher;
import com.chenruichao.service.ISeckillVoucherService;
import com.chenruichao.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.chenruichao.utils.RedisConstants.SECKILL_BEGIN_KEY;
import static com.chenruichao.utils.RedisConstants.SECKILL_END_KEY;
import static com.chenruichao.utils.RedisConstants.SECKILL_STOCK_KEY;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        // 预热库存与活动时间，供 seckill.lua 判断（Lua 里以 Redis 服务器时间为准，不传 Java 时间）
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());

        LocalDateTime beginTime = voucher.getBeginTime();
        if (beginTime != null) {
            stringRedisTemplate.opsForValue().set(SECKILL_BEGIN_KEY + voucher.getId(),
                    String.valueOf(beginTime.atZone(ZoneId.systemDefault()).toEpochSecond()));
        }

        LocalDateTime endTime = voucher.getEndTime();
        if (endTime != null) {
            stringRedisTemplate.opsForValue().set(SECKILL_END_KEY + voucher.getId(),
                    String.valueOf(endTime.atZone(ZoneId.systemDefault()).toEpochSecond()));
        }
    }
}
