package cn.gsq.sdp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.ConfigItem
 *
 * @author : gsq
 * @date : 2023-03-31 21:51
 * @note : It's not technology, it's art !
 **/
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ConfigItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String key; // 配置项主键

    private String value;   // 配置项内容

    private String origin;  // 默认值

    private String separator;    // 分隔符（若是数组则不能为空）

    private Boolean isMust; // 是否必须

    private List<String> label;    // 配置项标签

    private String description; // 英文描述信息

    private String description_ch; // 中文描述信息

    private Boolean isReadOnly;//配置项是否只读

    private Boolean isHidden;//配置项是否隐藏

    private Boolean canDelete;//配置项是否可删除

    private Boolean isInDictionary=true;//是否在字典里,默认true

    private Boolean isInUsing;//字典里的配置项是否在使用中

    private Boolean isSysConfig;//该配置项是否是系统自动生成的
}
