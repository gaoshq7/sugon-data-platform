package cn.gsq.sdp.job.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Project : galaxy
 * Class : cn.gsq.sdp.core.serve.job.pojo.JobStepInfo
 *
 * @author : gsq
 * @date : 2023-03-24 10:23
 * @note : It's not technology, it's art !
 **/
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class JobStepInfo {

    private String id;  // 任务步骤的唯一标示

    private String name;    // 任务步骤的名称

    /**
     * @Description : 建造方法
     * @Param : [id, name]
     * @Return : cn.gsq.sdp.core.serve.job.pojo.JobStepInfo
     * @Author : gsq
     * @Date : 10:35 上午
     * @note : An art cell !
    **/
    public static JobStepInfo build(String id, String name) {
        return new JobStepInfo(id, name);
    }

}
