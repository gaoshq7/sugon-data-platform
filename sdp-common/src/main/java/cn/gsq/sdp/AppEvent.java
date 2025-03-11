package cn.gsq.sdp;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.AppEvent
 *
 * @author : gsq
 * @date : 2025-03-03 14:33
 * @note : It's not technology, it's art !
 **/
@Getter
@AllArgsConstructor
public enum AppEvent {

    INSTALLED(1, "installed", "安装"),    // 服务安装完成（服务已经处于可用状态）

    UNINSTALLED(2, "uninstalled", "卸载"),    // 服务卸载完成

    PREINSTALL(3, "preinstall", "预安装"),     // 服务将要安装

    PREUNINSTALL(4, "preuninstall", "预卸载");    // 服务将要卸载

    private final Integer code;   // 类型编号

    private final String name;  // 类型名称

    private final String cnname;  // 中文名称

    private static final HashMap<Integer, AppEvent> events = new HashMap<>();

    static {
        for(AppEvent event : AppEvent.values()){
            events.put(event.getCode(), event);
        }
    }

    public static AppEvent parse(Integer code){
        if(events.containsKey(code)){
            return events.get(code);
        }
        return null;
    }

}
