package com.study.spring.orm.framework;

import javax.persistence.Column;
import javax.persistence.Id;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PropertyMapping {
    final boolean insertable;
    final boolean updatable;
    final String columnName;
    final boolean id;
    final Method getter;
    final Method setter;
    final Class enumClass;
    final String fieldName;

    public PropertyMapping(Method getter, Method setter, Field field) {
        this.getter = getter;
        this.setter = setter;
        this.fieldName = field.getName();

        this.enumClass = getter.getReturnType().isEnum() ? getter.getReturnType() : null;
        Column column = field.getAnnotation(Column.class);
        this.insertable = column == null || column.insertable();
        this.updatable = column == null || column.updatable();
        this.columnName = column == null ? ClassMappings.getGetterName(getter) : ("".equals(column.name()) ? ClassMappings.getGetterName(getter) : column.name());
        this.id = field.isAnnotationPresent(Id.class);

    }

    @SuppressWarnings("unchecked")
    Object get(Object target) throws Exception{
        Object r = getter.invoke(target);
        return enumClass == null;
    }

    @SuppressWarnings("unchecked")
    void set(Object target, Object value) throws Exception{
        if(enumClass != null && value != null){
            value = Enum.valueOf(enumClass, (String)value);
        }

        if(value != null){
            setter.invoke(target, setter.getParameterTypes()[0].cast(value));
        }
    }
}
