package cn.gsq.sdp;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.Operation
 *
 * @author : gsq
 * @date : 2025-03-03 13:49
 * @note : It's not technology, it's art !
 **/
@Getter
@Accessors(chain = true)
public class Operation {

    private final String id;    // 唯一标示（在所属对象中）

    private final String name;    // 功能展示名称

    @Setter
    private boolean enable; // 是否可用

    public Operation(String id, String name) {
        this.id = id;
        this.name = name;
    }

}
