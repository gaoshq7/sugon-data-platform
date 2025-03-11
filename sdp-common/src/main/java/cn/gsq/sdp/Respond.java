package cn.gsq.sdp;

import cn.hutool.core.date.DateUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.Respond
 *
 * @author : gsq
 * @date : 2021-04-22 16:36
 * @note : It's not technology, it's art !
 **/
@Getter
@Setter
@Accessors(chain = true)
public class Respond implements Serializable {

    private String id;      // 请求id（保留字段）

    private boolean isSuccess;  // 执行结果

    private String msg;         // 结果信息

    private String timestamp;      // 执行时间 (格式：yyyy-MM-dd HH:mm:ss)

    // success

    public static Respond success(String id, String msg){
        return new Respond()
                .setSuccess(true)
                .setId(id)
                .setMsg(msg);
    }

    public static Respond success(String msg){
        return new Respond()
                .setSuccess(true)
                .setMsg(msg)
                .setTimestamp(DateUtil.now());
    }

    public static Respond success(){
        return new Respond()
                .setSuccess(true)
                .setTimestamp(DateUtil.now());
    }

    // failed

    public static Respond failed(String id, String msg){
        return new Respond()
                .setSuccess(false)
                .setId(id)
                .setMsg(msg)
                .setTimestamp(DateUtil.now());
    }

    public static Respond failed(String msg){
        return new Respond()
                .setSuccess(false)
                .setMsg(msg)
                .setTimestamp(DateUtil.now());
    }

    public static Respond failed(){
        return new Respond()
                .setSuccess(false)
                .setTimestamp(DateUtil.now());
    }

}
