package com.study.spring.orm.framework;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import javax.core.common.Page;
import javax.core.common.jdbc.BaseDao;
import javax.core.common.utils.BeanUtils;
import javax.core.common.utils.DataUtils;
import javax.core.common.utils.GenericsUtils;
import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseDaoSupport<T extends Serializable, PK extends Serializable> implements BaseDao<T, PK> {
    private Logger log = Logger.getLogger(BaseDaoSupport.class);

    private String tableName = "";

    private JdbcTemplate jdbcTemplateWrite;
    private JdbcTemplate jdbcTemplateReadOnly;

    private DataSource dataSourceReadOnly;
    private DataSource dataSourceWrite;

    private EntityOperation<T> op;


    public BaseDaoSupport() {
        try {
            Class<T> entityClass = GenericsUtils.getSuperClassGenricType(getClass(), 0);
            op = new EntityOperation<T>(entityClass, this.getPKColumn());
            this.setTableName(op.tableName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getTableName() {
        return tableName;
    }

    public DataSource getDataSourceReadOnly() {
        return dataSourceReadOnly;
    }

    public DataSource getDataSourceWrite() {
        return dataSourceWrite;
    }

    /**
     * 动态切换表名
     * @param tableName
     */
    public void setTableName(String tableName) {
        if (StringUtils.isEmpty(tableName)){
            this.tableName = op.tableName;
        }else {
            this.tableName = tableName;
        }
    }

    public void setDataSourceReadOnly(DataSource dataSourceReadOnly) {
        this.dataSourceReadOnly = dataSourceReadOnly;
        jdbcTemplateReadOnly = new JdbcTemplate(dataSourceReadOnly);
    }

    public void setDataSourceWrite(DataSource dataSourceWrite) {
        this.dataSourceWrite = dataSourceWrite;
        jdbcTemplateWrite = new JdbcTemplate(dataSourceWrite);
    }

    private JdbcTemplate jdbcTemplateReadOnly(){
        return this.jdbcTemplateReadOnly;
    }

    private JdbcTemplate jdbcTemplateWrite(){
        return this.jdbcTemplateWrite;
    }

    /**
     * 还原默认表名
     */
    protected void restoreTableName(){
        this.setTableName(op.tableName);
    }

    /**
     * 将对象解析为Map
     * @param entity
     * @return
     */
    protected Map<String, Object> parse(T entity){
        return op.parse(entity);
    }

    protected T get(PK id) throws Exception{
        return (T)this.doLoad(id, this.op.rowMapper);
    }

    /**
     * 获取默认的实例对象
     * @param pkValue
     * @param rowMapper
     * @param <T>
     * @return
     */
    private <T>T doLoad(Object pkValue, RowMapper<T> rowMapper) {
        Object obj = this.doLoad(getTableName(), getPKColumn(), pkValue, rowMapper);
        if(obj != null){
            return (T)obj;
        }
        return null;
    }

    private Object doLoad(String tableName, String pkName, Object pkValue, RowMapper rowMapper) {
        StringBuffer sb = new StringBuffer();
        sb.append("select * from ").append(tableName).append(" where ").append(pkName).append(" = ?");
        List<Object> list =this.jdbcTemplateReadOnly().query(sb.toString(), rowMapper, pkValue);
        if(list == null || list.isEmpty()){
            return null;
        }
        return list.get(0);
    }

    /**
     * 获取之间列名称，建议子类重写
     * @return
     */
    protected abstract String getPKColumn();

    /**
     * 获取全部对象
     *
     * @return
     * @throws Exception
     */
    protected List<T> getAll() throws Exception{
        String sql = "select " + op.allColumn + " from " + getTableName();
        return this.jdbcTemplateReadOnly().query(sql, this.op.rowMapper,new HashMap<String, Object>());
    }

    /**
     * 插入并返回id
     * @param entity 只要entity不等于null，就执行插入
     * @return
     * @throws Exception
     */
    public PK insertAndReturnId(T entity) throws Exception{
        return (PK)this.doInsertReturnKey(parse(entity));
    }

    /**
     * 插入一条记录
     * @param entity 只要entity不等于null，就执行插入
     * @return
     * @throws Exception
     */
    public boolean insert(T entity) throws Exception {
        return this.doInsert(parse(entity));
    }

    /**
     * 批量保存对象
     * @param list 待保存的对象List
     * @return
     * @throws Exception
     */
    public int insertAll(List<T> list) throws Exception{
        int count = 0;
        int len = list.size();
        int step = 50000;
        Map<String, PropertyMapping> pm = op.mappings;
        int maxPage = (len % step == 0) ? (len / step) : (len / step + 1);
        for (int i = 1; i <= maxPage; i++){
            Page<T> page = pagination(list, i, step);
            String sql = "insert into " +getTableName() + "(" + op.allColumn + ") values";
            StringBuffer valstr = new StringBuffer();
            Object[] values = new Object[pm.size() * page.getRows().size()];
            for (int j = 0; j < page.getRows().size(); j++) {
                if (j > 0 && j < page.getRows().size()){
                    valstr.append(",");
                }
                valstr.append("(");
                int k = 0;
                for (PropertyMapping propertyMapping : pm.values()) {
                    values[(j * pm.size()) + k] = propertyMapping.getter.invoke(page.getRows().get(i));
                    if(k > 0 && k < pm.size()){
                        valstr.append(",");
                    }
                    valstr.append("?");
                    k++;
                }
                valstr.append(")");
            }
            int result = jdbcTemplateWrite().update(sql + valstr.toString(), values);
            count += result;
        }
        return count;
    }

    protected boolean replaceOne(T entity) throws Exception{
        return this.doReplace(parse(entity));
    }

    protected int replaceAll(List<T> list) throws Exception{
        int count = 0;
        int len = list.size();
        int step = 50000;
        Map<String, PropertyMapping> pm = op.mappings;
        int maxPage = (len % step == 0) ? (len / step) : (len / step + 1);
        for (int i = 1; i <= maxPage; i++) {
            Page<T> page = pagination(list, i, step);
            String sql = "replace into " + getTableName() + "(" + op.allColumn + ") values ";
            StringBuffer valstr = new StringBuffer();
            Object[] values = new Object[pm.size() * page.getRows().size()];
            for (int j = 0; j < page.getRows().size(); j++) {
                if(j > 0 && j < page.getRows().size()){
                    valstr.append(",");
                }
                valstr.append("(");
                int k = 0;
                for (PropertyMapping propertyMapping : pm.values()){
                    values[(j * pm.size()) + k] = propertyMapping.getter.invoke(page.getRows().get(j));
                    if(k > 0 && k < pm.size()){
                        valstr.append(",");
                    }
                    valstr.append("?");
                    k++;
                }
                valstr.append(")");
            }
            int result = jdbcTemplateWrite().update(sql + valstr.toString(), values);
            count += result;
        }
        return count;
    }

    /**
     * 保存对象，如果对象存在则更新，否则插入
     * @param entity
     * @return
     * @throws Exception
     */
    protected boolean save(T entity) throws Exception{
        PK pkValue = (PK)op.pkField.get(entity);
        if(this.exists(pkValue)){
            return this.doUpdate(pkValue, parse(entity)) > 0;
        } else {
            return this.doInsert(parse(entity));
        }
    }

    /**
     * 保存并返回新的id，如果对象存在则更新，否则插入
     * @param entity
     * @return
     * @throws Exception
     */
    protected PK saveAndReturnId(T entity) throws Exception{
        Object o = op.pkField.get(entity);
        if(o == null){
            return (PK)this.doInsertReturnKey(parse(entity));
        }
        PK pkValue = (PK)o;
        if(this.exists(pkValue)){
            this.doUpdate(pkValue, parse(entity));
            return pkValue;
        } else {
            return (PK)this.doInsertReturnKey(parse(entity));
        }
    }

    /**
     * 更新对象
     * @param entity ID不能为空；如果ID为空，其他条件不能为空；都为空，非法，不予执行
     * @return
     * @throws Exception
     */
    public boolean update(T entity) throws Exception {
        return this.doUpdate(op.pkField.get(entity), parse(entity)) > 0;
    }

    /**
     * 使用SQL语句更新对象
     * @param sql 更新sql语句
     * @param args 参数对象
     * @return 更新记录数
     * @throws Exception
     */
    public int update(String sql, Object... args) throws Exception{
        return jdbcTemplateWrite().update(sql, args);
    }

    /**
     * 使用SQL语句更新对象
     * @param sql 更新sql语句
     * @param paramMap 参数对象
     * @return 更新记录数
     * @throws Exception
     */
    protected int update(String sql, Map<String, ?> paramMap) throws Exception{
        return jdbcTemplateWrite().update(sql, paramMap);
    }

    /**
     * 删除对象
     * @param entity ID不能为空；如果ID为空，其他条件不能为空；都为空，非法，不予执行
     * @return
     * @throws Exception
     */
    public boolean delete(T entity) throws Exception {
        return this.doDelete(op.pkField.get(entity)) > 0;
    }

    /**
     * 批量删除对象
     * @param list
     * @return
     * @throws Exception
     */
    public int deleteAll(List<T> list) throws Exception{
        String pkName = op.pkField.getName();
        int count = 0;
        int len = list.size();
        int step = 1000;
        Map<String, PropertyMapping> pm = op.mappings;
        int maxPage = (len % step == 0) ? (len / step) : (len / step + 1);
        for (int i = 1; i <= maxPage; i++) {
            StringBuffer valstr = new StringBuffer();
            Page<T> page =pagination(list, i, step);
            Object[] values = new Object[page.getRows().size()];

            for (int j = 0; j < page.getRows().size(); j++) {
                if(j > 0 && j < page.getRows().size()){
                    valstr.append(",");
                }
                values[j] = pm.get(pkName).getter.invoke(page.getRows().get(j));
                valstr.append("?");
            }

            String sql = "delete from " + getTableName() + " where " + pkName + " in (" + valstr.toString() + ")";
            int result = jdbcTemplateWrite().update(sql, values);
            count += result;
        }
        return count;
    }

    /**
     * 根据ID删除对象，如果有记录则删除，没有记录也不报异常
     * @param id
     * @throws Exception
     */
    protected void deleteByPK(PK id) throws Exception{
        this.doDelete(id);
    }

    /**
     * 根据属性名和属性值查询符合条件的唯一对象，没有符合条件的记录返回null
     * @param propertyName
     * @param value
     * @return
     * @throws Exception
     */
    protected T selectUnique(String propertyName, Object value) throws Exception{
        QueryRule queryRule = QueryRule.getInstance();
        queryRule.andEqual(propertyName, value);
        return this.selectUnique(queryRule);
    }

    /**
     * 根据查询规则查询符合条件的唯一对象
     * @param queryRule
     * @return
     */
    protected T selectUnique(QueryRule queryRule) throws Exception {
        List<T> list= select(queryRule);
        if(list.size() == 0){
            return null;
        } else if(list.size() == 1){
            return list.get(0);
        } else {
            throw new IllegalStateException("findUnique return " + list.size() + " record(s).");
        }
    }

    /**
     * 合并PO List对象
     * @param pojoList
     * @param poList
     * @param idName
     * @throws Exception
     */
    protected void mergeList(List<T> pojoList, List<T> poList, String idName) throws Exception{
        mergeList(pojoList, poList, idName, false);
    }

    /**
     * 合并PO List对象
     * @param pojoList
     * @param poList
     * @param idName
     * @param isCopeyNull
     * @throws Exception
     */
    protected void mergeList(List<T> pojoList, List<T> poList, String idName, boolean isCopeyNull) throws Exception {
        Map<Object, Object> map = new HashMap<Object, Object>();
        Map<String, PropertyMapping> pm = op.mappings;
        for (Object element : pojoList) {
            Object key;
            key = pm.get(idName).getter.invoke(element);
            map.put(key, element);
        }
        for (Iterator<T> iterable = poList.iterator(); ((Iterator) iterable).hasNext();){
            T element = iterable.next();
            try {

                Object key = pm.get(idName).getter.invoke(element);
                if (!map.containsKey(key)) {
                    delete(element);
                    iterable.remove();
                } else {
                    DataUtils.copySimpleObject(map.get(key), element, isCopeyNull);
                }
            } catch (Exception e){
                throw new IllegalArgumentException(e);
            }
        }
        T[] pojoArray = (T[])pojoList.toArray();
        for (T element : pojoArray) {
            try {
                Object key = pm.get(idName).getter.invoke(element);
                if (key == null) {
                    poList.add(element);
                }
            } catch (Exception e){
                throw new IllegalArgumentException(e);
            }
        }

    }


    public Page<T> select(QueryRule queryRule, final int pageNo, final int pageSize) throws Exception {
        QueryRuleSqlBuilder builder = new QueryRuleSqlBuilder(queryRule);
        Object[] values = builder.getValueArr();
        String ws = removeFirstAnd(builder.getWhereSql());
        String whereSql = "".equals(ws) ? ws :(" where " + ws);
        String countSql = "select count(1) from " + getTableName() + whereSql;
        long count = (Long)this.jdbcTemplateReadOnly().queryForMap(countSql, values).get("count(1)");
        if (count == 0){
            return new Page<T>();
        }
        long start = (pageNo - 1) * pageSize;

        String orderSql = builder.getOrderSql();
        orderSql = (StringUtils.isEmpty(orderSql) ? " " : (" order by " + orderSql));
        String sql = "select " + op.allColumn + " from " + getTableName() + whereSql + orderSql + " limit " + start + "," + pageSize;
        List<T> list = (List<T>)this.jdbcTemplateReadOnly().query(sql, this.op.rowMapper, values);

        return new Page<T>(pageSize, start, list, count);
    }

    public List<T> select(QueryRule queryRule) throws Exception{
        QueryRuleSqlBuilder builder = new QueryRuleSqlBuilder(queryRule);
        String ws = removeFirstAnd(builder.getWhereSql());
        String whereSql = "".equals(ws) ? ws : (" where " + ws);
        String sql = "select " +op.allColumn + " from " + getTableName() + whereSql;
        Object[] values = builder.getValueArr();
        String orderSql = builder.getOrderSql();
        orderSql = StringUtils.isEmpty(orderSql) ? " " : (" order by " + orderSql);
        sql += orderSql;
        return (List<T>)this.jdbcTemplateReadOnly().query(sql, this.op.rowMapper, values);
    }

    protected  List<Map<String, Object>> selectBySql(String sql, Map<String, ?>param) throws Exception{
        return this.jdbcTemplateReadOnly().queryForList(sql, param);
    }

    protected Map<String, Object> selectUniqueBySql(String sql, Map<String, ?>param) throws Exception{
        List<Map<String, Object>> list = selectBySql(sql,param);
        if(list.size() == 0){
            return null;
        } else if (list.size() == 1){
            return list.get(0);
        } else {
            throw new IllegalStateException("findUnique return " + list.size() + " record(s)");
        }
    }

    public List<Map<String, Object>> selectBySql(String sql, Object... args) throws Exception{
        return this.jdbcTemplateReadOnly().queryForList(sql, args);
    }

    protected Map<String, Object> selectUniqueBySql(String sql, Object... param) throws Exception{
        List<Map<String, Object>> list = selectBySql(sql,param);
        if(list.size() == 0){
            return null;
        } else if (list.size() == 1){
            return list.get(0);
        } else {
            throw new IllegalStateException("findUnique return " + list.size() + " record(s)");
        }
    }

    protected List<Map<String,Object>> selectBySql(String sql,List<Object> list) throws Exception{
        return this.jdbcTemplateReadOnly().queryForList(sql,list.toArray());
    }

    protected Map<String,Object> selectUniqueBySql(String sql,List<Object> listParam) throws Exception{
        List<Map<String,Object>> listMap = selectBySql(sql, listParam);
        if (listMap.size() == 0) {
            return null;
        } else if (listMap.size() == 1) {
            return listMap.get(0);
        } else {
            throw new IllegalStateException("findUnique return " + listMap.size() + " record(s).");
        }
    }

    protected Page<Map<String,Object>> selectBySqlToPage(String sql, Map<String,?> param, final int pageNo, final int pageSize) throws Exception {
        String countSql = "select count(1) from (" + sql + ") a";
        long count = (Long) this.jdbcTemplateReadOnly().queryForMap(countSql,param).get("count(1)");

        if (count == 0) {
            return new Page<Map<String,Object>>();
        }
        long start = (pageNo - 1) * pageSize;
        // 有数据的情况下，继续查询
        sql = sql + " limit " + start + "," + pageSize;
        List<Map<String,Object>> list = (List<Map<String,Object>>) this.jdbcTemplateReadOnly().queryForList(sql, param);

        return new Page<Map<String,Object>>(pageSize, start, list, count);
    }

    public Page<Map<String,Object>> selectBySqlToPage(String sql, Object [] param, final int pageNo, final int pageSize) throws Exception {
        String countSql = "select count(1) from (" + sql + ") a";

        long count = (Long) this.jdbcTemplateReadOnly().queryForMap(countSql,param).get("count(1)");
        if (count == 0) {
            return new Page<Map<String,Object>>();
        }
        long start = (pageNo - 1) * pageSize;
        sql = sql + " limit " + start + "," + pageSize;
        List<Map<String,Object>> list = (List<Map<String,Object>>) this.jdbcTemplateReadOnly().queryForList(sql, param);
        log.debug(sql);
        return new Page<Map<String,Object>>(pageSize, start, list,count);
    }

    protected T selectUnique(Map<String, Object> properties) throws Exception {
        QueryRule queryRule = QueryRule.getInstance();
        for (String key : properties.keySet()) {
            queryRule.andEqual(key, properties.get(key));
        }
        return selectUnique(queryRule);
    }



    /**
     * 根据主键判断对象是否存在
     * @param id 对象的id
     * @return 如果存在返回true，否则返回false
     * @throws Exception
     */
    protected boolean exists(PK id) throws Exception {
        return null != this.doLoad(id, this.op.rowMapper);
    }

    /**
     * 查询满足条件的记录数
     * @param queryRule
     * @return
     * @throws Exception
     */
    protected long getCount(QueryRule queryRule) throws Exception{
        QueryRuleSqlBuilder builder = new QueryRuleSqlBuilder(queryRule);
        Object[] values = builder.getValueArr();
        String ws = removeFirstAnd(builder.getWhereSql());
        String whereSql = "".equals(ws) ? ws : (" where " + ws);
        String countSql = "select count(1) from " + getTableName() + whereSql;
        return (Long)this.jdbcTemplateReadOnly().queryForMap(countSql, values).get("count(1)");
    }

    protected T getMax(String propertyName) throws Exception{
        QueryRule queryRule = QueryRule.getInstance();
        queryRule.addDescOrder(propertyName);
        Page<T> result = this.select(queryRule, 1, 1);
        if (result.getRows() == null || result.getRows().size() == 0){
            return null;
        }
        return result.getRows().get(0);
    }

    private String removeFirstAnd(String whereSql) {
        if (StringUtils.isEmpty(whereSql)){
            return whereSql;
        }
        return whereSql.trim().toLowerCase().replaceAll("\\s*and", "") + " ";
    }

    // 根据当前list进行相应的分页返回
    protected Page<T> pagination(List<T> objList, int pageNo, int pageSize) throws Exception {
        List<T> objectArray = new ArrayList<T>(0);
        int startIndex = (pageNo - 1) * pageSize;
        int endIndex = pageNo * pageSize;
        if (endIndex >= objList.size()){
            endIndex = objList.size();
        }
        for (int i = startIndex; i < endIndex; i++) {
            objectArray.add(objList.get(i));
        }
        return new Page<T>(pageSize, startIndex, objectArray, objList.size());
    }

    protected <T> T populate(ResultSet rs, T obj){
        try {
            // 获取结果集的元元素
            ResultSetMetaData metaData = rs.getMetaData();
            // 获取所有列的个数
            int colCount = metaData.getColumnCount();
            Field[] fields = obj.getClass().getDeclaredFields();
            for (Field field : fields) {
                for (int i = 1; i <= colCount; i++) {
                    Object value = rs.getObject(i);
                    String colName = metaData.getColumnName(i);
                    if (!field.getName().equalsIgnoreCase(colName)){
                        continue;
                    }

                    try {
                        BeanUtils.copyProperty(obj, field.getName(), value);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return obj;
    }

    protected <T> T selectForObject(String sql, RowMapper<T> mapper, Object... args){
        List<T> results = this.jdbcTemplateReadOnly().query(sql, mapper, args);
        return DataAccessUtils.singleResult(results);
    }



    private Serializable doInsertReturnKey(Map<String,Object> params) {
        final List<Object> values = new ArrayList<Object>();
        final String sql = makeSimpleInsertSql(getTableName(), params, values);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        final JdbcTemplate jdbcTemplate = new JdbcTemplate(getDataSourceWrite());

        try {

            jdbcTemplate.update(new PreparedStatementCreator() {
                public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < values.size(); i++) {
                        ps.setObject(i + 1, values.get(i) == null ? null : values.get(i));
                    }
                    return ps;
                }
            }, keyHolder);

        } catch (DataAccessException e){
            e.printStackTrace();
        }

        if(keyHolder == null){
            return "";
        }

        Map<String, Object> keys = keyHolder.getKeys();
        if(keys == null || keys.size() == 0 || keys.values().size() == 0){
            return "";
        }
        Object key =  keys.values().toArray()[0];
        if(key == null || !(key instanceof Serializable)){
            return "";
        }
        if(key instanceof Number){
            Class clazz = key.getClass();
            return (clazz == int.class || clazz == Integer.class) ? ((Number) key).intValue() : ((Number)key).longValue();
        } else if(key instanceof String){
            return (String)key;
        } else{
            return (Serializable)key;
        }
    }

    // 插入
    private boolean doInsert(Map<String, Object> params) {
        String sql = this.makeSimpleInsertSql(this.getTableName(), params);
        int ret = this.jdbcTemplateWrite().update(sql, params.values().toArray());
        return ret > 0;
    }

    /**
     * 更新实例对象，返回删除记录数
     * @param pkValue
     * @param params
     * @return
     */
    private int doUpdate(Object pkValue, Map<String, Object> params) {
        String sql = this.makeDefaultSimpleUpdateSql(pkValue, params);
        params.put(this.getPKColumn(), pkValue);
        int ret=this.jdbcTemplateWrite().update(sql, params.values().toArray());
        return ret;
    }

    private boolean doReplace(Map<String, Object> params) {
        String sql = this.makeSimpleReplaceSql(this.getTableName(), params);
        int ret = this.jdbcTemplateWrite().update(sql, params.values().toArray());
        return ret > 0;
    }

    /**
     * 删除默认实例对象，返回删除记录数
     * @param pkValue
     * @return
     */
    private int doDelete(Object pkValue) {
        return this.doDelete(getTableName(), getPKColumn(), pkValue);
    }

    private int doDelete(String tableName, String pkName, Object pkValue) {
        StringBuffer sb = new StringBuffer();
        sb.append("delete from ").append(tableName).append(" where ").append(pkName).append(" = ?");
        int ret = this.jdbcTemplateWrite().update(sb.toString(), pkValue);
        return ret;
    }



    private <T> Page simplePageQuery(String sql, RowMapper<T> rowMapper, Map<String, ?> args, long pageNo, long pageSize){
        long start = (pageNo - 1) * pageSize;
        return simplePageQueryByStart(sql, rowMapper, args, start, pageSize);
    }

    private <T> Page simplePageQueryByStart(String sql, RowMapper<T> rowMapper, Map<String, ?> args, long start, long pageSize) {
        // 查询总数
        String countSql = "select count(1) " + removeSelect(removeOrders(sql));

        long count = (Long)this.jdbcTemplateReadOnly().queryForMap(countSql, args).get("count(1)");
        if (count == 0){
            return new Page();
        }
        sql = sql + " limit " + start + "," + pageSize;
        List<T> list = this.jdbcTemplateReadOnly().query(sql, rowMapper, args);
        return new Page((int)pageSize, start, list, count);
    }

    private String removeSelect(String sql) {
        int beginPos = sql.toLowerCase().indexOf("from");
        return sql.substring(beginPos);
    }

    private String removeOrders(String sql) {
        Pattern pattern = Pattern.compile("order\\s*by[\\w|\\W|\\s|\\S]*", Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "");

        }
        m.appendTail(sb);
        return sb.toString();
    }


    // 生成对象insert语句，简化sql拼接
    private String makeSimpleInsertSql(String tableName, Map<String, Object> params, List<Object> values) {
        if(StringUtils.isEmpty(tableName) || params == null || params.isEmpty()){
            return "";
        }
        StringBuffer sb = new StringBuffer();
        sb.append("insert into ").append(tableName);

        StringBuffer sbKey = new StringBuffer();
        StringBuffer sbValue = new StringBuffer();

        sbKey.append("(");
        sbValue.append("(");

        // 添加参数
        Set<String> set = params.keySet();
        int index = 0;
        for (String key : set) {
            sbKey.append(key);
            sbValue.append(" ?");
            if(index != set.size() - 1){
                sbKey.append(",");
                sbValue.append(",");
            }
            index++;
            values.add(params.get(key));
        }
        sbKey.append(")");
        sbValue.append(")");

        sb.append(sbKey).append("VALUES").append(sbValue);

        return sb.toString();
    }

    // 生成对象Insert语句，简化sql拼接
    private String makeSimpleInsertSql(String tableName, Map<String,Object> params) {
        if(StringUtils.isEmpty(tableName) || params == null || params.isEmpty()){
            return "";
        }
        StringBuffer sb = new StringBuffer();
        sb.append("insert into ").append(tableName);

        StringBuffer sbKey = new StringBuffer();
        StringBuffer sbValue = new StringBuffer();

        sbKey.append("(");
        sbValue.append("(");

        Set<String> set = params.keySet();
        int index = 0;
        for (String key : set) {
            sbKey.append(key);
            sbValue.append(" ?");
            if(index != set.size() - 1){
                sbKey.append(",");
                sbValue.append(",");
            }
            index++;
        }
        sbKey.append(")");
        sbValue.append(")");

        sb.append(sbKey).append("VALUES").append(sbValue);

        return sb.toString();
    }

    // 生成默认的对象update语句，简化sql拼接
    private String makeDefaultSimpleUpdateSql(Object pkValue, Map<String, Object> params) {
        return this.makeSimpleUpdateSql(getTableName(), getPKColumn(), pkValue, params);
    }

    // 生成默认的对象update语句，简化sql拼接
    private String makeSimpleUpdateSql(String tableName, String pkName, Object pkValue, Map<String,Object> params) {
        if (StringUtils.isEmpty(tableName) || params == null || params.isEmpty()){
            return "";
        }

        StringBuffer sb = new StringBuffer();
        sb.append("update ").append(tableName).append(" set ");

        Set<String> set = params.keySet();
        int index = 0;
        for (String key : set) {
            sb.append(key).append(" = ?");
            if(index != set.size() - 1){
                sb.append(",");
            }
            index++;
        }
        sb.append(" where ").append(pkName).append(" = ?");
        params.put("where_" + pkName, params.get(pkName));

        return sb.toString();
    }

    // 生成对象replace语句，简化sql拼接
    private String makeSimpleReplaceSql(String tableName, Map<String,Object> params) {
        if (StringUtils.isEmpty(tableName) || params == null || params.isEmpty()){
            return "";
        }
        StringBuffer sb = new StringBuffer();
        sb.append("replace into ").append(tableName);

        StringBuffer sbKey = new StringBuffer();
        StringBuffer sbValue = new StringBuffer();

        sbKey.append("(");
        sbValue.append("(");

        Set<String> set = params.keySet();
        int index = 0;
        for (String key : set) {
            sbKey.append(key);
            sbValue.append(" :").append(key);
            if(index != set.size() - 1){
                sbKey.append(",");
                sbValue.append(",");
            }
            index++;
        }
        sbKey.append(")");
        sbValue.append(")");

        sb.append(sbKey).append("VALUES").append(sbValue);

        return sb.toString();

    }

    // 生成对象replace语句，简化sql拼接
    private String makeSimpleReplaceSql(String tableName, Map<String,Object> params, List<Object> values) {
        if (StringUtils.isEmpty(tableName) || params == null || params.isEmpty()){
            return "";
        }
        StringBuffer sb = new StringBuffer();
        sb.append("replace into ").append(tableName);

        StringBuffer sbKey = new StringBuffer();
        StringBuffer sbValue = new StringBuffer();

        sbKey.append("(");
        sbValue.append("(");

        Set<String> set = params.keySet();
        int index = 0;
        for (String key : set) {
            sbKey.append(key);
            sbValue.append(" ?");
            if(index != set.size() - 1){
                sbKey.append(",");
                sbValue.append(",");
            }
            index++;
            values.add(params.get(key));
        }
        sbKey.append(")");
        sbValue.append(")");

        sb.append(sbKey).append("VALUES").append(sbValue);

        return sb.toString();
    }


}
