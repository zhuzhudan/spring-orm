package javax.core.common.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

/**
 * 提供各种对数据进行处理的方法
 */
public class DataUtils {
    private static final BigDecimal ONE = new BigDecimal("1");

    private static Map<Class, String> supportTypeMap = new HashMap<Class, String>();

    static {
        supportTypeMap.put(Integer.class, "");
        supportTypeMap.put(Long.class, "");
        supportTypeMap.put(Double.class, "");
        supportTypeMap.put(Byte.class, "");
        supportTypeMap.put(Character.class, "");
        supportTypeMap.put(Short.class, "");
        supportTypeMap.put(Float.class, "");
        supportTypeMap.put(Boolean.class, "");
        supportTypeMap.put(int.class, "");
        supportTypeMap.put(long.class, "");
        supportTypeMap.put(double.class, "");
        supportTypeMap.put(byte[].class, "");
        supportTypeMap.put(char.class, "");
        supportTypeMap.put(short.class, "");
        supportTypeMap.put(float.class, "");
        supportTypeMap.put(boolean.class, "");

        supportTypeMap.put(Date.class, "");
        supportTypeMap.put(BigDecimal.class, "");
        supportTypeMap.put(String.class, "");
    }

    private DataUtils(){}


    // 添加mergePO时支持的类型
    public static void addSupportType(Class clazz){
        supportTypeMap.put(clazz, "");
    }

    // 当整型数值为0时，返回字符串""，否则将整型值转化为字符串返回
    public static String zeroToEmpty(int i){
        return i == 0 ? "" : String.valueOf(i);
    }

    // 当浮点型数值为0时，返回字符串""，否则将浮点型值转化为字符串返回
    public static String zeroToEmpty(double d){
        return d == 0 ? "" : String.valueOf(d);
    }

    // 当字符串为null时，返回字符串""
    public static String nullToEmpty(String str){
        return str == null ? "" : str;
    }

    // 当字符串为""时，返回null
    public static String emptyToNull(String str){
        if(str == null){
            return null;
        }
        if(str.trim().length() == 0){
            return null;
        }
        return str;
    }

    // 当字符串为"null"或为null时，返回字符串""
    public static String dbNullToEmpty(String str){
        if (str == null || str.equalsIgnoreCase("null")){
            return "";
        }
        return str;
    }

    // 当字符串为null或""或全部为空格时,返回字符串"0",否则将字符串原封不动的返回
    public static String nullToZero(String str){
        if (str == null || str.trim().length() == 0){
            return "0";
        }
        return str;
    }

    // 对表达布尔型含义的字符串转换为中文的"是"/"否"
    public static String getBooleanDescribe(String str){
        if(str == null){
            throw new IllegalArgumentException("argument is null");
        }
        if(str.equalsIgnoreCase("y") || str.equalsIgnoreCase("yes")
                || str.equalsIgnoreCase("true") || str.equalsIgnoreCase("t")
                || str.equalsIgnoreCase("是") || str.equalsIgnoreCase("1")){
            return "是";
        } else if (str.equalsIgnoreCase("n") || str.equalsIgnoreCase("no")
                || str.equalsIgnoreCase("false") || str.equalsIgnoreCase("f")
                || str.equalsIgnoreCase("否") || str.equalsIgnoreCase("0")){
            return "否";
        } else if(str.trim().equals("")){
            return "";
        }
        throw  new IllegalArgumentException("argument not in ('y','n','yes','no','true','false','t','f','是','否','1','0','')");
    }

    // 对表达布尔型含义的字符串转换为boolean型的true/false
    public static boolean getBoolean(String str){
        if(str == null){
            throw new IllegalArgumentException("argument is null");
        }
        if(str.equalsIgnoreCase("y") || str.equalsIgnoreCase("yes")
                || str.equalsIgnoreCase("true") || str.equalsIgnoreCase("t")
                || str.equalsIgnoreCase("是") || str.equalsIgnoreCase("1")){
            return true;
        } else if (str.equalsIgnoreCase("n") || str.equalsIgnoreCase("no")
                || str.equalsIgnoreCase("false") || str.equalsIgnoreCase("f")
                || str.equalsIgnoreCase("否") || str.equalsIgnoreCase("0")){
            return false;
        } else if(str.trim().equals("")){
            return false;
        }
        throw  new IllegalArgumentException("argument not in ('y','n','yes','no','true','false','t','f','是','否','1','0','')");
    }

    // 返回对应boolean型变量的字符串型中文描述：'是'/'否'
    public static String getBooleanDescribe(boolean bln){
        if (bln){
            return getBooleanDescribe("true");
        }
        return getBooleanDescribe("false");
    }

    // 比较两个存放了数字的字符串的大小，如果不为数字将抛出异常.
    public static int compareByValue(String str1, String str2){
        BigDecimal big1 = new BigDecimal(str1);
        BigDecimal big2 = new BigDecimal(str2);
        return big1.compareTo(big2);
    }

    // 提供精确的小数位四舍五入处理
    public static double round(double value, int scale){
        BigDecimal bigDecimal = new BigDecimal(Double.toString(value));
        return bigDecimal.divide(ONE, scale, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    // 拷贝简单对象.(null也将拷贝)
    public static void copySimpleObject(Object source, Object target){
        copySimpleObject(source, target, true);
    }

    public static void copySimpleObject(Object source, Object target, boolean isCopyNull){
        if (target == null || source == null){
            return;
        }
        List targetMethodList = BeanUtils.getSetter(target.getClass());
        List sourceMethodList = BeanUtils.getGetter(source.getClass());
        Map<String, Method> map = new HashMap<String, Method>();
        for (Iterator iterator = sourceMethodList.iterator(); iterator.hasNext();){
            Method method = (Method)iterator.next();
            map.put(method.getName(), method);
        }
        for (Iterator iterator = targetMethodList.iterator();iterator.hasNext();){
            Method method = (Method)iterator.next();
            String fieldName = method.getName().substring(3);
            Method sourceMethod =(Method)map.get("get" + fieldName);
            if(sourceMethod == null){
                sourceMethod = (Method)map.get("is" + fieldName);
            }
            if (sourceMethod == null){
                continue;
            }
            if (!supportTypeMap.containsKey(sourceMethod.getReturnType())){
                continue;
            }

            try {
                Object value = sourceMethod.invoke(source, new Object[0]);
                if (isCopyNull){
                    method.invoke(target, new Object[]{value});
                } else {
                    if (value != null){
                        method.invoke(target, new Object[]{value});
                    }
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }
}
