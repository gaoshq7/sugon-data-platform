package cn.gsq.sdp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 配置项比较
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ConfigCompare {

    private String key; // 配置项主键

    private String version1;   // 版本1

    private String version2;  // 版本2

    private String origin;    // 默认值

}
