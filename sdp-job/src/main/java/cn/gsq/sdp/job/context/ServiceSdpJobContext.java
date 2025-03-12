package cn.gsq.sdp.job.context;

import cn.gsq.task.context.BaseContext;
import cn.gsq.task.pojo.OpParams;
import cn.hutool.core.lang.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Project : galaxy
 * Class : cn.gsq.sdp.core.serve.job.context.SystemSdpJobContext
 *
 * @author : gsq
 * @date : 2023-03-14 10:36
 * @note : It's not technology, it's art !
 **/
@Getter
@Setter
@Accessors(chain = true)
public class ServiceSdpJobContext<T extends OpParams> extends BaseContext {

    private T params;//参数的实体类

    /**
     * @Description : 构造器
     * @Param : [id, num]
     * @Return :
     * @Author : gsq
     * @Date : 11:43 上午
     * @note : An art cell !
    **/
    public ServiceSdpJobContext(T params) {
        super(UUID.fastUUID().toString(), UUID.fastUUID().toString());
        this.params=params;
    }

}
