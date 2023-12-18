package cc.dodder.common.util.snowflake;

/**
 * 雪花算法
 * @author jerry
 * @date 2023/11/2 16:42
 */
public class IdUtils {

    public static String genFileNo(){
        return "F".concat(String.valueOf(genThreeId()));
    }
    public static String genOrderNo(){
        return "D".concat(String.valueOf(genOneId()));
    }

    public static String genUserNo(){
        return "U".concat(String.valueOf(genTwoId()));
    }

    public static String genTokenNo(){
        return "T".concat(String.valueOf(genTwoId()));
    }

    public static long genTwoId(){
        IdWork worker = new IdWork(2,2,1);
        return worker.nextId();
    }

    public static long genOneId(){
        IdWork worker = new IdWork(1,1,1);
        return worker.nextId();
    }

    public static long genThreeId(){
        IdWork worker = new IdWork(3,3,1);
        return worker.nextId();
    }

    public static String genRandom() {
        return String.valueOf(genThreeId());
    }
}
