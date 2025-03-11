package cn.gsq.sdp.core;

import java.util.Map;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.Configuration
 *
 * @author : gsq
 * @date : 2025-02-27 15:00
 * @note : It's not technology, it's art !
 **/
public interface Configuration extends Thing {

    /**
     * @Description : 全量更新分支配置文件内容
     **/
    void updateConfig(String branch, Map<String, String> items);

    @Override
    default Type getType() {
        return Type.CONFIG;
    }

}
