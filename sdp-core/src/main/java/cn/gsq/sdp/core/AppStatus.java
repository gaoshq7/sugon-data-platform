package cn.gsq.sdp.core;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Getter
@AllArgsConstructor
public enum AppStatus {


    STARTING("启动中"){

    },

    STOPPING("停止中"){

    },

    RESTARTING("重启中"){

    },

    CHECK_AVAILABLE("是否可用"){
        @Override
        public boolean isAvailable(AbstractApp app) {
            return super.isAvailable(app);
        }
    };

    private final String name;  // 类型

    public boolean isAvailable(AbstractApp app){
        return false;
    }


}
