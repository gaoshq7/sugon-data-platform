package cn.gsq.sdp;

import cn.hutool.core.date.DateUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.RpcRequest
 *
 * @author : gsq
 * @date : 2021-04-28 10:59
 * @note : It's not technology, it's art !
 **/
@Setter
@Getter
@Accessors(chain = true)
public class RpcRequest extends Request {

    private String hostname;

    private String home;

    private String order;

    public RpcRequest(){}

    public RpcRequest(String hostname, String home, String order){
        this.hostname = hostname;
        this.home = home;
        this.order = order;
        this.setTimestamp(DateUtil.now());
    }

    public RpcRequest(String id, String hostname, String home, String orders){
        this.setId(id);
        this.hostname = hostname;
        this.home = home;
        this.order = order;
        this.setTimestamp(DateUtil.now());
    }

    public static RpcRequest createRequest(String hostname, String home, String order){
        return new RpcRequest(hostname, home, order);
    }

    public static RpcRequest createRequest(String id, String hostname, String home, String order){
        return new RpcRequest(id, hostname, home, order);
    }

}
