package cn.gsq.sdp;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ConfigItemHistory {
    private String serveName;//服务名
    private String fileName;//文件名
    private String currentVersion;//当前版本
    private String oldVersion;//上一个版本
    private String configItemName;//配置项key
    private String oldValue;//旧值
    private String currentValue;//新值
    private String originalValue;//默认值
    private String updateTime;//修改时间
}
