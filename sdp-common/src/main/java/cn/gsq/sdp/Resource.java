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

    private String pkg;   // 服务对应的安装包根目录名称

    private String hostname;    // 下载安装包的主机名

    private String path;        // 包安装地址（例如：/usr/sdp/v5.3.1）

}
