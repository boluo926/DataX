package com.alibaba.datax.plugin.rdbms.writer.util;

import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.rdbms.util.DBUtil;
import com.alibaba.datax.plugin.rdbms.util.DBUtilErrorCode;
import com.alibaba.datax.plugin.rdbms.util.DataBaseType;
import com.alibaba.datax.plugin.rdbms.util.RdbmsException;
import com.alibaba.datax.plugin.rdbms.writer.Constant;
import com.alibaba.datax.plugin.rdbms.writer.Key;
import com.alibaba.druid.sql.parser.ParserException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public final class WriterUtil {
    private static final Logger LOG = LoggerFactory.getLogger(WriterUtil.class);

    //TODO 切分报错
    public static List<Configuration> doSplit(Configuration simplifiedConf,
                                              int adviceNumber) {

        List<Configuration> splitResultConfigs = new ArrayList<Configuration>();

        int tableNumber = simplifiedConf.getInt(Constant.TABLE_NUMBER_MARK);

        //处理单表的情况
        if (tableNumber == 1) {
            //由于在之前的  master prepare 中已经把 table,jdbcUrl 提取出来，所以这里处理十分简单
            for (int j = 0; j < adviceNumber; j++) {
                splitResultConfigs.add(simplifiedConf.clone());
            }

            return splitResultConfigs;
        }

        if (tableNumber != adviceNumber) {
            throw DataXException.asDataXException(DBUtilErrorCode.CONF_ERROR,
                    String.format("您的配置文件中的列配置信息有误. 您要写入的目的端的表个数是:%s , 但是根据系统建议需要切分的份数是：%s. 请检查您的配置并作出修改.",
                            tableNumber, adviceNumber));
        }

        String jdbcUrl;
        List<String> preSqls = simplifiedConf.getList(Key.PRE_SQL, String.class);
        List<String> postSqls = simplifiedConf.getList(Key.POST_SQL, String.class);

        List<Object> conns = simplifiedConf.getList(Constant.CONN_MARK,
                Object.class);

        for (Object conn : conns) {
            Configuration sliceConfig = simplifiedConf.clone();

            Configuration connConf = Configuration.from(conn.toString());
            jdbcUrl = connConf.getString(Key.JDBC_URL);
            sliceConfig.set(Key.JDBC_URL, jdbcUrl);

            sliceConfig.remove(Constant.CONN_MARK);

            List<String> tables = connConf.getList(Key.TABLE, String.class);

            for (String table : tables) {
                Configuration tempSlice = sliceConfig.clone();
                tempSlice.set(Key.TABLE, table);
                tempSlice.set(Key.PRE_SQL, renderPreOrPostSqls(preSqls, table));
                tempSlice.set(Key.POST_SQL, renderPreOrPostSqls(postSqls, table));

                splitResultConfigs.add(tempSlice);
            }

        }

        return splitResultConfigs;
    }

    public static List<String> renderPreOrPostSqls(List<String> preOrPostSqls, String tableName) {
        if (null == preOrPostSqls) {
            return Collections.emptyList();
        }

        List<String> renderedSqls = new ArrayList<String>();
        for (String sql : preOrPostSqls) {
            //preSql为空时，不加入执行队列
            if (StringUtils.isNotBlank(sql)) {
                renderedSqls.add(sql.replace(Constant.TABLE_NAME_PLACEHOLDER, tableName));
            }
        }

        return renderedSqls;
    }

    public static void executeSqls(Connection conn, List<String> sqls, String basicMessage,DataBaseType dataBaseType) {
        Statement stmt = null;
        String currentSql = null;
        try {
            stmt = conn.createStatement();
            for (String sql : sqls) {
                currentSql = sql;
                DBUtil.executeSqlWithoutResultSet(stmt, sql);
            }
        } catch (Exception e) {
            throw RdbmsException.asQueryException(dataBaseType,e,currentSql,null,null);
        } finally {
            DBUtil.closeDBResources(null, stmt, null);
        }
    }

    public static String getWriteTemplate(List<String> columnHolders, List<String> valueHolders, String writeMode, DataBaseType dataBaseType, boolean forceUseUpdate) {
        boolean isWriteModeLegal = writeMode.trim().toLowerCase().startsWith("insert")
                || writeMode.trim().toLowerCase().startsWith("replace")
                || writeMode.trim().toLowerCase().startsWith("update");

        if (!isWriteModeLegal) {
            throw DataXException.asDataXException(DBUtilErrorCode.ILLEGAL_VALUE,
                    String.format("您所配置的 writeMode:%s 错误. 因为DataX 目前仅支持replace,update 或 insert 方式. 请检查您的配置并作出修改.", writeMode));
        }
        // && writeMode.trim().toLowerCase().startsWith("replace")
        String writeDataSqlTemplate;
        if (forceUseUpdate ||
                ((dataBaseType == DataBaseType.MySql || dataBaseType == DataBaseType.Tddl) && writeMode.trim().toLowerCase().startsWith("update"))
                ) {
            //update只在mysql下使用

            writeDataSqlTemplate = new StringBuilder()
                    .append("INSERT INTO %s (").append(StringUtils.join(columnHolders, ","))
                    .append(") VALUES(").append(StringUtils.join(valueHolders, ","))
                    .append(")")
                    .append(onDuplicateKeyUpdateString(columnHolders))
                    .toString();
        } else {

            //这里是保护,如果其他错误的使用了update,需要更换为replace
            if (writeMode.trim().toLowerCase().startsWith("update")) {
                writeMode = "replace";
            }
            writeDataSqlTemplate = new StringBuilder().append(writeMode)
                    .append(" INTO %s (").append(StringUtils.join(columnHolders, ","))
                    .append(") VALUES(").append(StringUtils.join(valueHolders, ","))
                    .append(")").toString();
        }

        return writeDataSqlTemplate;
    }

    public static String onDuplicateKeyUpdateString(List<String> columnHolders){
        if (columnHolders == null || columnHolders.size() < 1) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(" ON DUPLICATE KEY UPDATE ");
        boolean first = true;
        for(String column:columnHolders){
            if(!first){
                sb.append(",");
            }else{
                first = false;
            }
            sb.append(column);
            sb.append("=VALUES(");
            sb.append(column);
            sb.append(")");
        }

        return sb.toString();
    }

    /**
     * 获取表的主键列列表
     *
     * @param connection 数据库连接
     * @param tableName 表名
     * @return 主键列名列表
     */
    public static List<String> getPrimaryKeys(Connection connection, String tableName) {
        List<String> primaryKeys = new ArrayList<String>();
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet rs = metaData.getPrimaryKeys(null, null, tableName);
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
            rs.close();
        } catch (SQLException e) {
            LOG.warn("获取表 {} 的主键信息失败: {}", tableName, e.getMessage());
        }
        return primaryKeys;
    }

    /**
     * 为达梦数据库构建 MERGE INTO SQL
     * 达梦数据库使用 MERGE INTO 实现 replace/update 模式
     *
     * @param table 表名
     * @param columns 列名列表
     * @param primaryKeys 主键列名列表
     * @return MERGE INTO SQL 语句
     */
    public static String buildMergeIntoSql(String table, List<String> columns, List<String> primaryKeys) {
        if (primaryKeys == null || primaryKeys.isEmpty()) {
            throw new IllegalArgumentException("达梦数据库的 replace/update 模式需要表有主键");
        }

        // 构建 USING 子句 - 使用 DUAL 表提供值
        StringBuilder usingClause = new StringBuilder("(SELECT ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                usingClause.append(", ");
            }
            usingClause.append("? AS ").append(columns.get(i));
        }
        usingClause.append(" FROM DUAL) T2");

        // 构建 ON 条件 - 基于主键列
        StringBuilder onClause = new StringBuilder();
        for (int i = 0; i < primaryKeys.size(); i++) {
            if (i > 0) {
                onClause.append(" AND ");
            }
            onClause.append("T1.").append(primaryKeys.get(i))
                    .append(" = T2.").append(primaryKeys.get(i));
        }

        // 构建 UPDATE SET 子句 - 更新所有非主键列
        StringBuilder updateSetClause = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            // 跳过主键列（主键不需要更新）
            if (primaryKeys.contains(column)) {
                continue;
            }
            if (updateSetClause.length() > 0) {
                updateSetClause.append(", ");
            }
            updateSetClause.append("T1.").append(column)
                    .append(" = T2.").append(column);
        }

        // 构建 INSERT 子句
        StringBuilder insertClause = new StringBuilder();
        if (updateSetClause.length() > 0) {
            insertClause.append("INSERT (").append(StringUtils.join(columns, ", "))
                    .append(") VALUES (T2.")
                    .append(StringUtils.join(columns, ", T2."))
                    .append(")");
        } else {
            // 如果所有列都是主键，则只需插入主键
            insertClause.append("INSERT (").append(StringUtils.join(primaryKeys, ", "))
                    .append(") VALUES (T2.")
                    .append(StringUtils.join(primaryKeys, ", T2."))
                    .append(")");
        }

        // 组装完整的 MERGE INTO 语句
        StringBuilder mergeSql = new StringBuilder();
        mergeSql.append("MERGE INTO ").append(table).append(" T1 ");
        mergeSql.append("USING ").append(usingClause).append(" ");
        mergeSql.append("ON (").append(onClause).append(") ");

        if (updateSetClause.length() > 0) {
            mergeSql.append("WHEN MATCHED THEN UPDATE SET ").append(updateSetClause).append(" ");
        }
        mergeSql.append("WHEN NOT MATCHED THEN ").append(insertClause);

        return mergeSql.toString();
    }

    public static void preCheckPrePareSQL(Configuration originalConfig, DataBaseType type) {
        List<Object> conns = originalConfig.getList(Constant.CONN_MARK, Object.class);
        Configuration connConf = Configuration.from(conns.get(0).toString());
        String table = connConf.getList(Key.TABLE, String.class).get(0);

        List<String> preSqls = originalConfig.getList(Key.PRE_SQL,
                String.class);
        List<String> renderedPreSqls = WriterUtil.renderPreOrPostSqls(
                preSqls, table);

        if (null != renderedPreSqls && !renderedPreSqls.isEmpty()) {
            LOG.info("Begin to preCheck preSqls:[{}].",
                    StringUtils.join(renderedPreSqls, ";"));
            for(String sql : renderedPreSqls) {
                try{
                    DBUtil.sqlValid(sql, type);
                }catch(ParserException e) {
                    throw RdbmsException.asPreSQLParserException(type,e,sql);
                }
            }
        }
    }

    public static void preCheckPostSQL(Configuration originalConfig, DataBaseType type) {
        List<Object> conns = originalConfig.getList(Constant.CONN_MARK, Object.class);
        Configuration connConf = Configuration.from(conns.get(0).toString());
        String table = connConf.getList(Key.TABLE, String.class).get(0);

        List<String> postSqls = originalConfig.getList(Key.POST_SQL,
                String.class);
        List<String> renderedPostSqls = WriterUtil.renderPreOrPostSqls(
                postSqls, table);
        if (null != renderedPostSqls && !renderedPostSqls.isEmpty()) {

            LOG.info("Begin to preCheck postSqls:[{}].",
                    StringUtils.join(renderedPostSqls, ";"));
            for(String sql : renderedPostSqls) {
                try{
                    DBUtil.sqlValid(sql, type);
                }catch(ParserException e){
                    throw RdbmsException.asPostSQLParserException(type,e,sql);
                }

            }
        }
    }


}
