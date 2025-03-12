package cn.gsq.sdp.job.context;

import cn.gsq.sdp.HostInfo;
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
 * Class : cn.gsq.sdp.core.serve.job.context.HostSdpJobContext
 *
 * @author : xyy
 * @date : 2024-05-29 14:32
 * @note : It's not technology, it's art !
 **/

@Getter
@Setter
@Accessors(chain = true)
public class HostSdpJobContext extends BaseContext {

    private transient Map<String, Queue<HostInfo>> hostDriver;
    /**
     * @param hostDriver
     * @Description : 构造
     * @Return :
     * @Author : xyy
     * @Date : 17:44
     * @note : An art cell !
     */
    public HostSdpJobContext(Map<String, Queue<HostInfo>> hostDriver) {
        super(UUID.fastUUID().toString(), UUID.fastUUID().toString());
        this.hostDriver=hostDriver;
    }
}
