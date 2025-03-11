package cn.gsq.sdp;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.Request
 *
 * @author : gsq
 * @date : 2021-04-22 16:35
 * @note : It's not technology, it's art !
 **/
@Setter
@Getter
@Accessors(chain = true)
public class Request implements Serializable {

    private String id;      // 请求id

    private String content;     // 请求内容

    private String timestamp;      // 请求时间 (格式：yyyy-MM-dd HH:mm:ss)

}
