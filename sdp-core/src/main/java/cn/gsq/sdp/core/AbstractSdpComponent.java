package cn.gsq.sdp.core;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.AbstractSdpComponent
 *
 * @author : gsq
 * @date : 2025-04-03 14:51
 * @note : It's not technology, it's art !
 **/
public abstract class AbstractSdpComponent extends AbstractBeansAssemble {

    /**
     * @Description : 初始化固定属性
     **/
    protected abstract void initProperty();

    /**
     * @Description : 加载SDP组件环境信息
     **/
    protected abstract void loadEnvResource();

    /**
     * @Description : 还原初始化状态
     **/
    protected abstract void recover();

}
