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

import static com.chenruichao.utils.RedisConstants.ORDER_KEY;
import static com.chenruichao.utils.RedisConstants.QUEUE_CONSUMER;
import static com.chenruichao.utils.RedisConstants.QUEUE_GROUP;
import static com.chenruichao.utils.RedisConstants.QUEUE_NAME;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final RedisIdWorker redisIdWorker;

    private final ISeckillVoucherService seckillVoucherService;

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newFixedThreadPool(1);

    private static final DefaultRedisScript<Long> defaultRedisScript;

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
        initConsumerGroup();
        SECKILL_ORDER_EXECUTOR.submit(new orderHandlerTask());
    }

    // 启动前确保消费组已建好 避免组不存在时消费者空转刷异常
    private void initConsumerGroup() {
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(QUEUE_NAME))) {
            log.info("消息队列 {} 还不存在，跳过消费组初始化", QUEUE_NAME);
            return;
        }

        boolean exists = stringRedisTemplate.opsForStream().groups(QUEUE_NAME).stream()
                .anyMatch(g -> QUEUE_GROUP.equals(g.groupName()));

        if (exists) {
            log.info("消费组 {} 已存在", QUEUE_GROUP);
            return;
        }

        stringRedisTemplate.opsForStream().createGroup(QUEUE_NAME, QUEUE_GROUP);
        log.info("消费组 {} 创建成功", QUEUE_GROUP);
    }

    // 消费者监听redis消息队列
    private class orderHandlerTask implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> res = stringRedisTemplate.opsForStream().read(
                        Consumer.from(QUEUE_GROUP, QUEUE_CONSUMER),
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

                    stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, QUEUE_GROUP, record.getId());
                    stringRedisTemplate.opsForStream().delete(QUEUE_NAME, record.getId());
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
                    Consumer.from(QUEUE_GROUP, QUEUE_CONSUMER),
                    StreamReadOptions.empty().count(1),
                    StreamOffset.create(QUEUE_NAME, ReadOffset.from("0"))
                );

                if (res == null || res.isEmpty()) {
                    break;
                }

                MapRecord<String, Object, Object> record = res.get(0);
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(record.getValue(), new VoucherOrder(), true);

                proxy.createVoucherOrder(voucherOrder);

                stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, QUEUE_GROUP, record.getId());
                stringRedisTemplate.opsForStream().delete(QUEUE_NAME, record.getId());
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
            return Result.fail(switch ((int) res) {
                case 1 -> "库存不足";
                case 2 -> "不能重复下单";
                case 3 -> "秒杀尚未开始";
                case 4 -> "秒杀已结束";
                default -> "下单失败";
            });
        }

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
