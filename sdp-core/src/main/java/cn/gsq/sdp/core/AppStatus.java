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

    OPERATING("操作中"){

    },

    INSTALLING("安装中"){

    },

    UNINSTALLING("卸载中"){

    },

    RUNNING("正常"){

    },

    FAULT("故障"){

    },

    CHECK_AVAILABLE("是否可用"){
        @Override
        public boolean isAvailable(AbstractApp app) {
            return app.isAvailable();
        }
    };

    private final String name;  // 类型

    public boolean isAvailable(AbstractApp app){
        return false;
    }


}
