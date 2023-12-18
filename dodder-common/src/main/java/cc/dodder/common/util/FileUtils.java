package cc.dodder.common.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author jerry
 * @since 2023/12/18 11:37
 */
public class FileUtils {
    /**
     * 将内容写入文件
     * @param fileContent 文件内容
     * @param fileName 文件名称 完整路径
     */
    public static void saveFile(String fileContent,String fileName) throws IOException {
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(fileName))) {
            bufferedWriter.write(fileContent);
        }
    }
}
