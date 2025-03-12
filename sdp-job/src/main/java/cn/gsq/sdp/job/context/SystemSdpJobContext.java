package cn.gsq.sdp.job.context;

import cn.gsq.graph.dag.Vertex;
import cn.gsq.sdp.core.AbstractServe;
import cn.gsq.task.context.BaseContext;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.map.MapUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Map;
import java.util.Queue;

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
public class SystemSdpJobContext extends BaseContext {

    private transient Map<String, Queue<Vertex<AbstractServe>>> data;

    /**
     * @Description : 构造器
     * @Param : [id, num]
     * @Return :
     * @Author : gsq
     * @Date : 11:43 上午
     * @note : An art cell !
    **/
    public SystemSdpJobContext(Map<String, Queue<Vertex<AbstractServe>>> data) {
        super(UUID.fastUUID().toString(), UUID.fastUUID().toString());
        this.data=data;
    }

}
