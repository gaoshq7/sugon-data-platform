package cn.gsq.sdp;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.WebUI
 *
 * @author : xyy
 * @date : 2024-09-20 15:08
 * @note : It's not technology, it's art !
 **/
@Setter
@Getter
@Accessors(chain = true)
public class WebUI {

    private String name;    //ui名

    private String url;     //ui连接

}
