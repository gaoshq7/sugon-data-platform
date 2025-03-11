package cn.gsq.sdp.job;

import cn.gsq.task.XmlPlanParser;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

@Component("xmlParserJob")
public class XmlParser implements XmlPlanParser {

    @Override
    public String getPlanXml() {
        // 获取类路径下的xml文件路径
        ClassLoader classLoader = XmlParser.class.getClassLoader();
        // 加载资源
        InputStream inputStream = classLoader.getResourceAsStream("flow-sdp.xml");
        String fileContent = null;
        if (inputStream != null) {
            try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
                // 使用Scanner读取输入流中的内容
                StringBuilder stringBuilder = new StringBuilder();
                while (scanner.hasNextLine()) {
                    stringBuilder.append(scanner.nextLine()).append("\n");
                }
                fileContent = stringBuilder.toString();
            } catch (Exception e) {
                throw new RuntimeException("flow-sdp.xml读取异常");
            }
        }

        return fileContent;
    }

}
