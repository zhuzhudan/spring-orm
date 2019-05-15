package com.study.spring.orm.framework;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.RowMapper;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 实体对象的反射操作
 * @param <T>
 */
public class EntityOperation<T> {
    private Logger log = Logger.getLogger(EntityOperation.class);

    // 泛型实体Class对象
    public Class<T> entityClass = null;
    public final Map<String, PropertyMapping> mappings;
    public final RowMapper<T> rowMapper;

    public final String tableName;
    public String allColumn = "*";
    public Field pkField;

    public EntityOperation(Class<T> clazz, String pk) throws Exception {
        if(!clazz.isAnnotationPresent(Entity.class)){
            throw new Exception("在" + clazz.getName() + "中没有找到Entity注解，不能做ORM映射");
        }

        this.entityClass = clazz;
        Table table = entityClass.getAnnotation(Table.class);
        if(table != null){
            this.tableName = table.name();
        } else {
            this.tableName = entityClass.getSimpleName();
        }

        Map<String, Method> getters = ClassMappings.findPublicGetters(entityClass);
        Map<String, Method> setters = ClassMappings.findPublicGetters(entityClass);
        Field[] fields = ClassMappings.findFields(entityClass);
        fillPkFieldAndAllColumn(pk, fields);

        this.mappings = getPropertyMappings(getters, setters, fields);
        this.allColumn = this.mappings.keySet().toString().replace("[","").replace("]","").replaceAll(" ","");
        this.rowMapper = createRowMapper();
    }

    private RowMapper<T> createRowMapper() {
        return new RowMapper<T>() {
            public T mapRow(ResultSet resultSet, int rowNum) throws SQLException {
                try {
                    T t = entityClass.newInstance();
                    ResultSetMetaData meta = resultSet.getMetaData();
                    int columns = meta.getColumnCount();
                    String columnName;
                    for (int i = 1; i <= columns; i++) {
                        Object value = resultSet.getObject(i);
                        columnName = meta.getColumnName(i);
                        fillBeanFieldValue(t, columnName, value);
                    }
                    return t;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

    }

    private Map<String, PropertyMapping> getPropertyMappings(Map<String, Method> getters, Map<String, Method> setters, Field[] fields) {
        Map<String, PropertyMapping> mappings = new HashMap<String, PropertyMapping>();
        String name;
        for (Field field : fields) {
            if(field.isAnnotationPresent(Transient.class)){
                continue;
            }

            name = field.getName();
            if(name.startsWith("is")){
                name = name.substring(2);
            }
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
            Method setter = setters.get(name);
            Method getter = getters.get(name);
            if(setter == null || getter == null){
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            if(column == null){
                mappings.put(field.getName(), new PropertyMapping(getter, setter, field));
            } else {
                mappings.put(column.name(), new PropertyMapping(getter, setter, field));
            }
        }
        return mappings;

    }

    // 设定主键
    private void fillPkFieldAndAllColumn(String pk, Field[] fields) {
        if(!StringUtils.isEmpty(pk)){
            try {
                pkField = entityClass.getDeclaredField(pk);
                pkField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
        }
        for (Field field : fields) {
            if(StringUtils.isEmpty(pk)){
                Id id = field.getAnnotation(Id.class);
                if(id != null){
                    pkField = field;
                    break;
                }
            }
        }

    }

    public T parse(ResultSet rs){
        T t = null;
        if(null == rs){
            return null;
        }
        Object value = null;
        try {
            t = (T)entityClass.newInstance();
            for (String columnName : mappings.keySet()) {
                value = rs.getObject(columnName);
                fillBeanFieldValue(t, columnName, value);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return t;
    }

    private void fillBeanFieldValue(T t, String columnName, Object value) {
        if(value != null){
            PropertyMapping pm = mappings.get(columnName);
            if(pm != null){
                try {
                    pm.set(t, value);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Map<String, Object> parse(T t){
        Map<String, Object> map = new TreeMap<String, Object>();
        try {
            for (String columnName : mappings.keySet()) {
                Object value = mappings.get(columnName).getter.invoke(t);
                if(value == null){
                    continue;
                }
                map.put(columnName, value);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return map;
    }

    public void println(T t){
        try {
            for (String columnName : mappings.keySet()) {
                Object value = mappings.get(columnName).getter.invoke(t);
                if(value == null){
                    continue;
                }
                System.out.println(columnName + " = " + value);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
