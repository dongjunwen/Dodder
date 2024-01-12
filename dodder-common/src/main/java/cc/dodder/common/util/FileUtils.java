package cc.dodder.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jerry
 * @since 2023/12/18 11:37
 */
public class FileUtils {
    private static final Logger logger= LoggerFactory.getLogger(FileUtils.class);

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

    /**
     * 读取文件内容 按照list返回
     * @param fileName 文件内容
     * @return
     */
    public static List<String> readFileList(String fileName) {
        List<String> retList=new ArrayList<>();
        try{
            File file=new File(fileName);
            if(!file.exists()){
                file.createNewFile();
            }
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String bufferText=null;
            int lineNum=1;
            while ((bufferText=bufferedReader.readLine())!=null){
                logger.info("文件:{}第{}行,读取内容:{}",fileName,lineNum,bufferText);
                retList.add(bufferText);
                lineNum++;
            }
        }catch (Exception e){
            logger.info("读取文件:{0},发生异常:{}",fileName,e);
        }
        return retList;
    }

    /**
     * 读取文件内容
     * @param file
     * @return
     */
    public static List<String> readFileContent(File file) {
        List<String> retList=new ArrayList<>();
        if(!file.exists()){
            return null;
        }
        String fileName=file.getName();

        try{
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String bufferText=null;
            int lineNum=1;
            while ((bufferText=bufferedReader.readLine())!=null){
                logger.info("文件:{}第{}行,读取内容:{}",fileName,lineNum,bufferText);
                retList.add(bufferText);
                lineNum++;
            }
        }catch (Exception e){
            logger.info("读取文件:{0},发生异常:{}",fileName,e);
        }
        return retList;
    }
}
