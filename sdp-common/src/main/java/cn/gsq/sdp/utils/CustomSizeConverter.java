package cn.gsq.sdp.utils;

/**
 * Project : galaxy
 * Class : cn.gsq.sdp.util.CustomSizeConverter
 *
 * @author : xyy
 * @date : 2025-01-07 09:34
 * @note : It's not technology, it's art !
 **/
public class CustomSizeConverter {
    public static double convertToTargetUnit(long sizeInBytes, String targetUnit) {
        // 将目标单位转换为对应的倍数
        long targetMultiplier;
        switch (targetUnit.toUpperCase()) {
            case "KB":
                targetMultiplier = 1024;
                break;
            case "MB":
                targetMultiplier = 1024*1024;
                break;
            case "GB":
                targetMultiplier = 1024*1024*1024;
                break;
            case "TB":
                targetMultiplier = 1024L *1024*1024*1024;
                break;
            case "PB":
                targetMultiplier = 1024L *1024*1024*1024*1024;
                break;
            case "EB":
                targetMultiplier = 1024L *1024*1024*1024*1024*1024;
                break;
            case "B": // 默认字节
            default:
                targetMultiplier = 1;
                break;
        }

        // 按目标单位计算大小
        double result = (double) sizeInBytes / targetMultiplier;

        // 保留两位小数
        return Math.round(result * 100.0) / 100.0;
    }

    // 将数字类型按照单位转换为long类型
    public static Long convert2Long(Object number, String unit) {
        if (number == null || unit == null || unit.isEmpty()) {
            throw new IllegalArgumentException("Number and unit cannot be null or empty");
        }
        // 将输入的 number 转为 double 以便计算
        double numericValue;
        if (number instanceof Number) {
            numericValue = ((Number) number).doubleValue();
        } else {
            throw new IllegalArgumentException("Number must be of type long, double, or int");
        }
        // 单位转换因子
        long factor;
        switch (unit.toUpperCase()) {
            case "B": // 字节
                factor = 1;
                break;
            case "KB": // 千字节
                factor = 1024L;
                break;
            case "MB": // 兆字节
                factor = 1024L * 1024;
                break;
            case "GB": // 吉字节
                factor = 1024L * 1024 * 1024;
                break;
            case "TB": // 太字节
                factor = 1024L * 1024 * 1024 * 1024;
                break;
            case "PB": // 拍字节
                factor = 1024L * 1024 * 1024 * 1024 * 1024;
                break;
            default:
                throw new IllegalArgumentException("Unsupported unit: " + unit);
        }
        // 计算并转换为 long 类型
        double result = numericValue * factor;
        if (result > Long.MAX_VALUE || result < Long.MIN_VALUE) {
            throw new ArithmeticException("Result exceeds long type range");
        }
        return (long) result;
    }

    public static void main(String[] args) {
        System.out.println(convertToTargetUnit(10240000, "MB")); // 输出：9.77
        System.out.println(convertToTargetUnit(10240000, "KB")); // 输出：10000.0
        System.out.println(convertToTargetUnit(10240000, "GB")); // 输出：0.01
        System.out.println(convertToTargetUnit(10240000, "B"));  // 输出：10240000.0
    }
}
