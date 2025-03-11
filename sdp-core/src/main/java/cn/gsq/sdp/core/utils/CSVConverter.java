package cn.gsq.sdp.core.utils;

import cn.gsq.sdp.ConfigItem;
import cn.hutool.core.io.resource.ClassPathResource;
import com.alibaba.fastjson.JSONObject;
import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class CSVConverter {

    public static List<ConfigItem> convertCSV(String filePath){
        List<ConfigItem> list=new ArrayList<>();

        ClassPathResource resource = new ClassPathResource(filePath);
        try {
            // 使用OpenCSV解析CSV文件
            CSVReader reader = new CSVReader(new InputStreamReader(resource.getStream()));
            reader.readNext();//表头
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                ConfigItem item = new ConfigItem();
//

                if(nextLine.length==10){
                    item.setKey(nextLine[0]);
                    item.setOrigin(nextLine[1]);
                    //处理默认值
                    if(nextLine[2].equals("TRUE")||nextLine[2].equals("FALSE")){
                        item.setValue(nextLine[2].toLowerCase());
                    }else {
                        item.setValue(nextLine[2]);
                    }

                    item.setDescription(nextLine[3]);
                    item.setDescription_ch(nextLine[4]);
                    item.setIsMust(Boolean.valueOf(nextLine[5]));
                    item.setSeparator(nextLine[6]);
                    item.setLabel(Arrays.asList(nextLine[7].split(",")));
                    int anInt = Integer.parseInt(nextLine[8]);
                    switch (anInt){
                        case 1:
                            item.setIsHidden(true);
                            item.setCanDelete(false);
                            break;
                        case 2:
                            item.setIsReadOnly(true);
                            item.setIsHidden(false);
                            item.setCanDelete(false);
                            break;
                        case 3:
                            item.setIsHidden(false);
                            item.setIsReadOnly(false);
                            item.setCanDelete(false);
                            break;
                        case 4:
                            item.setIsHidden(false);
                            item.setIsReadOnly(false);
                            item.setCanDelete(true);
                            break;
                    }
                    item.setIsSysConfig(Boolean.valueOf(nextLine[9]));
                }else {
                    item.setKey("content");//纯文本key固定
                    item.setValue(nextLine[0]);
                    int anInt = Integer.parseInt(nextLine[1]);
                    switch (anInt){
                        case 1:
                            item.setIsHidden(true);
                            item.setCanDelete(false);
                            break;
                        case 2:
                            item.setIsReadOnly(true);
                            item.setIsHidden(false);
                            item.setCanDelete(false);
                            break;
                        case 3:
                            item.setIsHidden(false);
                            item.setIsReadOnly(false);
                            item.setCanDelete(false);
                            break;
                        case 4:
                            item.setIsHidden(false);
                            item.setIsReadOnly(false);
                            item.setCanDelete(true);
                            break;
                    }
                    item.setIsMust(true);
                    item.setIsSysConfig(false);
                    item.setLabel(Arrays.asList(nextLine[2].split(",")));
                }

                list.add(item);
            }
        } catch (Exception e) {
            log.error("csv文件转换失败:"+e.getMessage());
            throw new RuntimeException("csv文件转换失败:"+e.getMessage());
        }

        return list;
    }

    public static void main(String[] args) {
        String s="d:\\xyy\\hdfs-site.csv";
        List<ConfigItem> list = convertCSV(s);
        System.out.println(JSONObject.toJSONString(list));
    }

}
