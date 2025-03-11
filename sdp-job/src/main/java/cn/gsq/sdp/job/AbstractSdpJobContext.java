package cn.gsq.sdp.job;

import cn.hutool.core.util.NumberUtil;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Project : galaxy
 * Class : cn.gsq.sdp.core.serve.job.SdpJobContext
 *
 * @author : gsq
 * @date : 2023-03-13 17:51
 * @note : It's not technology, it's art !
 **/
@Getter
public abstract class AbstractSdpJobContext {

    protected final String id;    // 任务唯一ID

    protected final Integer total;    // 需要运行的步骤总数量

    private Integer current=0;  // 当前完成步骤数量

    /**
     * @Description : 构造器
     * @Param : [id, snum]
     * @Return :
     * @Author : gsq
     * @Date : 11:42 上午
     * @note : ⚠️ 需要初始化任务id、任务需要运行的总步骤数 !
    **/
    protected AbstractSdpJobContext(String id, Integer snum) {
        this.id = id;
        this.total = snum;
    }

    /**
     * @Description : 获取当前任务的完成进度
     * @Param : []
     * @Return : java.math.BigDecimal
     * @Author : gsq
     * @Date : 1:14 下午
     * @note : An art cell !
    **/
    public BigDecimal getPlan() {
        return NumberUtil.round(NumberUtil.div(current, total).floatValue(), 2);
    }

    /**
     * @Description : 完成整个任务的一步
     * @Param : []
     * @Return : void
     * @Author : gsq
     * @Date : 1:13 下午
     * @note : ⚠️ 该接口对外提供，线程安全，当达到步骤总数的时候将不在增加 !
    **/
    public void onceComplete() {
        synchronized (this) {
            if(total >= 1 && current < total)
                current++;
        }
    }

}
