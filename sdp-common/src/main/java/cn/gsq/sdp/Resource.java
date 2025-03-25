package cn.gsq.sdp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.Resource
 *
 * @author : gsq
 * @date : 2025-03-25 14:42
 * @note : It's not technology, it's art !
 **/
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Resource {

    private String version;     // sdp版本号

    private String servename;   // 服务名称

    private String hostname;    // 下载安装包的主机名

    private String path;        // 包安装地址

}
