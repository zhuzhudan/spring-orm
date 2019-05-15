package javax.core.common.utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class BeanUtils extends org.apache.commons.beanutils.BeanUtils {
    public static List<Method> getSetter(Class cl){
        List<Method> list = new ArrayList<Method>();
        Method[] methods = cl.getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if (!methodName.startsWith("set")){
                continue;
            }
            list.add(method);
        }
        while (true){
            cl = cl.getSuperclass();
            if(cl == Object.class){
                break;
            }
            list.addAll(getSetter(cl));
        }
        return list;
    }

    public static List<Method> getGetter(Class cl){
        List<Method> list = new ArrayList<Method>();
        Method[] methods = cl.getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if (!methodName.startsWith("get") && !methodName.startsWith("is")){
                continue;
            }
            list.add(method);
        }
        while (true){
            cl = cl.getSuperclass();
            if (cl == Object.class){
                break;
            }
            list.addAll(getGetter(cl));
        }
        return list;

    }
}
