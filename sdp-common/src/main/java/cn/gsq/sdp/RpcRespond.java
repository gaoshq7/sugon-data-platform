package cn.gsq.sdp;

import cn.gsq.sdp.utils.HostUtil;
import cn.hutool.core.date.DateUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.RpcRespond
 *
 * @author : gsq
 * @date : 2021-04-28 11:11
 * @note : It's not technology, it's art !
 **/
@Setter
@Getter
@Accessors(chain = true)
public class RpcRespond<T> extends Respond {

    private String hostname;    // 执行节点

    private T content;         // 返回实体内容

    public RpcRespond() {}

    public RpcRespond(boolean isSuccess, String msg, T content) {
        setSuccess(isSuccess);
        setHostname(HostUtil.getHostname());
        setMsg(msg);
        setContent(content);
        setTimestamp(DateUtil.now());
    }

    // 成功

    public static <T> RpcRespond<T> success(String msg, T content){
        return new RpcRespond<T>(true, msg, content);
    }

    public static <T> RpcRespond<T> success(T content){
        return new RpcRespond<T>(true, "操作成功", content);
    }

    public static RpcRespond<String> success(String msg){
        return new RpcRespond<String>(true, msg, "无详细信息");
    }

    public static RpcRespond<String> success(){
        return new RpcRespond<String>(true, "操作成功", "无详细信息");
    }

    // 失败

    public static <T> RpcRespond<T> failed(String msg, T content){
        return new RpcRespond<T>(false, msg, content);
    }

    public static <T> RpcRespond<T> failed(T content){
        return new RpcRespond<T>(false, "操作失败", content);
    }

    public static RpcRespond<String> failed(String msg){
        return new RpcRespond<String>(false, msg, "无详细信息");
    }

    public static RpcRespond<String> failed() {
        return new RpcRespond<String>(false, "操作失败", "无详细信息");
    }

}
