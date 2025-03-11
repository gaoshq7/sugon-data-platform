package cn.gsq.sdp.job.model;

import cn.gsq.task.pojo.OpParams;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CustomParams extends OpParams {

    private String subjectName;//执行主体名

    private String functionID;//函数id

}
