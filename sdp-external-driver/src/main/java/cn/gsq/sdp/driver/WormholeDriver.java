package cn.gsq.sdp.driver;

public interface WormholeDriver {

    /**
     * @Description : 获取wormhole脚本任务执行id
     */
    void id(String id);

    /**
     * @Description : 逐行处理wormhole脚本执行消息
     */
    void handle(String line);

}
