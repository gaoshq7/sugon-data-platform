package cn.gsq.sdp.core.utils;

import cn.hutool.core.thread.ThreadUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.utils.CommonUtil
 *
 * @author : gsq
 * @date : 2025-06-09 15:22
 * @note : It's not technology, it's art !
 **/
@Slf4j
public final class CommonUtil {

    private CommonUtil() {}

    public static boolean waitForSignal(Supplier<Boolean> condition, long timeout, long interval) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);

        try {
            while (System.nanoTime() < deadline) {
                if (condition.get()) {
                    return true; // 条件满足，提前放开
                }
                ThreadUtil.safeSleep(interval);
            }
        } catch (Exception e) {
            log.error("判断函数异常：", e);
            Thread.currentThread().interrupt();
            return false; // 中断退出
        }
        return false; // 超时退出
    }

}
