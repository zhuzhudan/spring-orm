package com.study.spring.orm.framework;

// SQL排序
public class Order {
    // 升序/降序
    private boolean ascending;
    // 根据哪个字段排序
    private String propertyName;

    @Override
    public String toString() {
        return "Order{" +
                propertyName + ' ' +
                (ascending ? "asc" : "desc") +
                '}';
    }

    public Order(boolean ascending, String propertyName) {
        this.ascending = ascending;
        this.propertyName = propertyName;
    }

    public static Order asc(String propertyName) {
        return new Order(true, propertyName);
    }

    public static Order desc(String propertyName){
        return new Order(false, propertyName);
    }

}
