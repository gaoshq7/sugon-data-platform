package cn.gsq.sdp.driver;

import cn.gsq.sdp.Blueprint;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.driver.ServeDriver
 *
 * @author : gsq
 * @date : 2025-03-03 16:55
 * @note : It's not technology, it's art !
 **/
public interface ServeDriver {

    /**
     * @Description : 服务安装回执
     * @Param : [serve]
     * @Return : void
     * @Author : gsq
     * @Date : 10:08 上午
     * @note : An art cell !
     **/
    void receiptInstallServe(Blueprint.Serve serve);

    /**
     * @Description : 服务卸载回执
     * @Param : [serve]
     * @Return : void
     * @Author : gsq
     * @Date : 9:05 上午
     * @note : An art cell !
     **/
    void receiptUninstallServe(String serve);

    /**
     * @Description : 服务安装后更新资源计划
     * @Param :
     * @Return :
     * @Author : xyy
     * @Date : 2024/12/13
     * @note : An art cell !
     **/
    void updateResourcePlan(String serve);

}
