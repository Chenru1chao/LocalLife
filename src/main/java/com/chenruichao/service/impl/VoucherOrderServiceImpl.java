package com.chenruichao.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chenruichao.dto.Result;
import com.chenruichao.entity.VoucherOrder;
import com.chenruichao.mapper.VoucherOrderMapper;
import com.chenruichao.service.ISeckillVoucherService;
import com.chenruichao.service.IVoucherOrderService;
import com.chenruichao.utils.RedisIdWorker;
import com.chenruichao.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final RedisIdWorker redisIdWorker;

    private final ISeckillVoucherService seckillVoucherService;

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newFixedThreadPool(1);

    private static final DefaultRedisScript<Long> defaultRedisScript;

    private static final String ORDER_KEY = "order";

    private static final String QUEUE_NAME = "stream.orders";

    @Lazy
    @Resource
    private IVoucherOrderService proxy;

    // 初始化加载lua脚本
    static {
        defaultRedisScript = new DefaultRedisScript<>();
        defaultRedisScript.setLocation(new ClassPathResource("seckill.lua"));
        defaultRedisScript.setResultType(Long.class);
    }

    @PostConstruct
    private void initTask() {
        SECKILL_ORDER_EXECUTOR.submit(new orderHandlerTask());
    }

    // 消费者监听redis消息队列
    private class orderHandlerTask implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> res = stringRedisTemplate.opsForStream().read(
                        Consumer.from("group1", "consumer1"),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(QUEUE_NAME, ReadOffset.lastConsumed())
                    );

                    if (res == null || res.isEmpty()) {
                        continue;
                    }

                    MapRecord<String, Object, Object> record = res.get(0);

                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(record.getValue(),
                            new VoucherOrder(), true);

                    proxy.createVoucherOrder(voucherOrder);

                    stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, "group1", record.getId());
                } catch (Exception e) {
                    handlePendingList();
                }
            }
        }
    }

    private void handlePendingList() {
        while (true) {
            try {
                List<MapRecord<String, Object, Object>> res = stringRedisTemplate.opsForStream().read(
                    Consumer.from("group1", "consumer1"),
                    StreamReadOptions.empty().count(1),
                    StreamOffset.create(QUEUE_NAME, ReadOffset.from("0"))
                );

                if (res == null || res.isEmpty()) {
                    break;
                }

                MapRecord<String, Object, Object> record = res.get(0);
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(record.getValue(), new VoucherOrder(), true);

                proxy.createVoucherOrder(voucherOrder);

                stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, "group1", record.getId());
            } catch (Exception e) {
                try {
                    log.error("Pending_list订单处理异常", e);
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        long orderId = redisIdWorker.nextId(ORDER_KEY);

        // 执行lua脚本 redis是单线程执行命令并且lua脚本保证原子性 防止多卖超卖
        // lua脚本使用乐观锁 先判断库存再扣减 效率非常高
        long res = stringRedisTemplate.execute(
                defaultRedisScript, Collections.emptyList(), voucherId.toString(),
                UserHolder.getUser().getId().toString(), String.valueOf(orderId));

        if (res != 0L) {
            return Result.fail(res == 1 ? "库存不足" : "不能重复下单");
        }

        // TODO:完成异步更新数据库
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);

        return Result.ok(orderId);
    }

    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long count = query().eq("user_id", voucherOrder.getUserId())
                .eq("voucher_id", voucherOrder.getVoucherId()).count();

        if (count >= 1) {
            log.error("不能重复下单");
            return;
        }

        boolean ok = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update();

        if (!ok) {
            log.error("库存不足");
            return;
        }

        save(voucherOrder);
    }
}
