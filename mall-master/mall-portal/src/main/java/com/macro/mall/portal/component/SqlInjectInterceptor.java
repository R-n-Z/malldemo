package com.macro.mall.portal.component;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL注入防护拦截器
 * 自动检测和阻止SQL注入攻击
 */
@Slf4j
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class SqlInjectInterceptor implements org.apache.ibatis.plugin.Interceptor {

    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SqlInjectInterceptor.class);

    /**
     * SQL注入特征检测
     */
    private static final Set<Pattern> INJECT_PATTERNS = new HashSet<>();

    static {
        // SQL注入特征模式
        INJECT_PATTERNS.add(Pattern.compile("(?i)(\\b(select|insert|update|delete|drop|truncate|alter|create|exec|execute|union|declare|xp_)\\b)"));
        INJECT_PATTERNS.add(Pattern.compile("(?i)(--|;|/\\*|\\*/|@|@@|char\\s*\\(|nchar\\s*\\(|varchar\\s*\\(|nvarchar\\s*\\()"));
        INJECT_PATTERNS.add(Pattern.compile("(?i)(\\bxp_\\w+\\b)"));
        INJECT_PATTERNS.add(Pattern.compile("(?i)(information_schema|sysobjects|syscolumns)"));
        INJECT_PATTERNS.add(Pattern.compile("(?i)(0x[0-9a-fA-F]+)"));
        INJECT_PATTERNS.add(Pattern.compile("(?i)(\\bwaitfor\\s+delay\\b)"));
        INJECT_PATTERNS.add(Pattern.compile("(?i)(\\bsleep\\s*\\()"));
        INJECT_PATTERNS.add(Pattern.compile("(?i)(benchmark\\s*\\()"));
        INJECT_PATTERNS.add(Pattern.compile("(?i)(load_file\\s*\\()"));
        INJECT_PATTERNS.add(Pattern.compile("(?i)(into\\s+outfile\\s*)"));
        INJECT_PATTERNS.add(Pattern.compile("(?i)(1=1|2=2|3=3)"));
        INJECT_PATTERNS.add(Pattern.compile("(?i)('\\s*or\\s*'\\s*1\\s*=\\s*1)"));
        INJECT_PATTERNS.add(Pattern.compile("(?i)(\\-\\-)"));
    }

    /**
     * 允许的SQL关键字白名单
     */
    private static final Set<String> ALLOWED_KEYWORDS = new HashSet<>();
    static {
        ALLOWED_KEYWORDS.add("SELECT");
        ALLOWED_KEYWORDS.add("INSERT");
        ALLOWED_KEYWORDS.add("UPDATE");
        ALLOWED_KEYWORDS.add("DELETE");
        ALLOWED_KEYWORDS.add("FROM");
        ALLOWED_KEYWORDS.add("WHERE");
        ALLOWED_KEYWORDS.add("AND");
        ALLOWED_KEYWORDS.add("OR");
        ALLOWED_KEYWORDS.add("JOIN");
        ALLOWED_KEYWORDS.add("LEFT");
        ALLOWED_KEYWORDS.add("RIGHT");
        ALLOWED_KEYWORDS.add("INNER");
        ALLOWED_KEYWORDS.add("OUTER");
        ALLOWED_KEYWORDS.add("ON");
        ALLOWED_KEYWORDS.add("GROUP BY");
        ALLOWED_KEYWORDS.add("ORDER BY");
        ALLOWED_KEYWORDS.add("LIMIT");
        ALLOWED_KEYWORDS.add("OFFSET");
        ALLOWED_KEYWORDS.add("HAVING");
        ALLOWED_KEYWORDS.add("COUNT");
        ALLOWED_KEYWORDS.add("SUM");
        ALLOWED_KEYWORDS.add("AVG");
        ALLOWED_KEYWORDS.add("MAX");
        ALLOWED_KEYWORDS.add("MIN");
        ALLOWED_KEYWORDS.add("DISTINCT");
        ALLOWED_KEYWORDS.add("AS");
        ALLOWED_KEYWORDS.add("IN");
        ALLOWED_KEYWORDS.add("NOT IN");
        ALLOWED_KEYWORDS.add("BETWEEN");
        ALLOWED_KEYWORDS.add("LIKE");
        ALLOWED_KEYWORDS.add("IS NULL");
        ALLOWED_KEYWORDS.add("IS NOT NULL");
        ALLOWED_KEYWORDS.add("EXISTS");
        ALLOWED_KEYWORDS.add("CASE");
        ALLOWED_KEYWORDS.add("WHEN");
        ALLOWED_KEYWORDS.add("THEN");
        ALLOWED_KEYWORDS.add("ELSE");
        ALLOWED_KEYWORDS.add("END");
        ALLOWED_KEYWORDS.add("VALUES");
        ALLOWED_KEYWORDS.add("SET");
        ALLOWED_KEYWORDS.add("INTO");
        ALLOWED_KEYWORDS.add("NULL");
        ALLOWED_KEYWORDS.add("TRUE");
        ALLOWED_KEYWORDS.add("FALSE");
        ALLOWED_KEYWORDS.add("ASC");
        ALLOWED_KEYWORDS.add("DESC");
    }

    /**
     * 危险参数值检测
     */
    private static final Set<Pattern> DANGEROUS_VALUES = new HashSet<>();
    static {
        DANGEROUS_VALUES.add(Pattern.compile("(?i).*'\\s*OR\\s*'.*"));
        DANGEROUS_VALUES.add(Pattern.compile("(?i).*'\\s*=\\s*'.*"));
        DANGEROUS_VALUES.add(Pattern.compile("(?i).*1\\s*=\\s*1.*"));
        DANGEROUS_VALUES.add(Pattern.compile("(?i).*OR\\s+1\\s*=\\s*1.*"));
        DANGEROUS_VALUES.add(Pattern.compile("(?i).*--.*"));
        DANGEROUS_VALUES.add(Pattern.compile("(?i).*\\/\\*.*"));
        DANGEROUS_VALUES.add(Pattern.compile("(?i).*\\s+WAITFOR\\s+DELAY.*"));
        DANGEROUS_VALUES.add(Pattern.compile("(?i).*\\s+SLEEP\\s*\\(.*"));
    }

    private Properties properties;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);

        // 获取SQL信息
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();
        BoundSql boundSql = statementHandler.getBoundSql();
        String sql = boundSql.getSql().trim();
        Object parameterObject = boundSql.getParameterObject();

        // 只对SELECT、INSERT、UPDATE、DELETE进行检测
        if (sqlCommandType == SqlCommandType.SELECT ||
            sqlCommandType == SqlCommandType.INSERT ||
            sqlCommandType == SqlCommandType.UPDATE ||
            sqlCommandType == SqlCommandType.DELETE) {
            
            // 检测SQL注入
            if (detectSqlInjection(sql, parameterObject)) {
                LOGGER.warn("SQL注入攻击检测: sql={}, type={}", sql, sqlCommandType);
                throw new SqlInjectException("检测到SQL注入攻击: " + sqlCommandType);
            }
        }

        return invocation.proceed();
    }

    /**
     * 检测SQL注入
     * @param sql 原始SQL
     * @param parameterObject 参数对象
     * @return true=检测到注入, false=正常
     */
    private boolean detectSqlInjection(String sql, Object parameterObject) {
        // 1. 检测SQL特征
        for (Pattern pattern : INJECT_PATTERNS) {
            if (pattern.matcher(sql).find()) {
                // 检查是否在白名单中
                if (!isAllowedKeyword(sql)) {
                    LOGGER.debug("检测到可疑SQL特征: {}", sql);
                    return true;
                }
            }
        }

        // 2. 检测参数值中的注入特征
        if (parameterObject != null) {
            if (detectParameterInjection(parameterObject)) {
                LOGGER.debug("检测到参数注入: {}", sql);
                return true;
            }
        }

        // 3. 检测OR条件滥用
        if (detectOrConditionAbuse(sql)) {
            LOGGER.debug("检测到OR条件滥用: {}", sql);
            return true;
        }

        // 4. 检测恒等式
        if (detectAlwaysTrueCondition(sql)) {
            LOGGER.debug("检测到恒等式条件: {}", sql);
            return true;
        }

        return false;
    }

    /**
     * 检测参数注入
     */
    private boolean detectParameterInjection(Object parameterObject) {
        try {
            // 获取参数值
            if (parameterObject instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> paramMap = (java.util.Map<String, Object>) parameterObject;
                for (Object value : paramMap.values()) {
                    if (value != null && isDangerousValue(value.toString())) {
                        return true;
                    }
                }
            } else {
                // 反射获取字段值
                Field[] fields = parameterObject.getClass().getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    Object value = field.get(parameterObject);
                    if (value != null && isDangerousValue(value.toString())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("检测参数注入失败", e);
        }
        return false;
    }

    /**
     * 检测危险参数值
     */
    private boolean isDangerousValue(String value) {
        for (Pattern pattern : DANGEROUS_VALUES) {
            if (pattern.matcher(value).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检测OR条件滥用
     * 例如: WHERE id=1 OR 1=1
     */
    private boolean detectOrConditionAbuse(String sql) {
        // 检测 OR 1=1 或 OR true
        if (sql.toUpperCase().contains(" OR ") && 
            (sql.toUpperCase().contains("1=1") || 
             sql.toUpperCase().contains("TRUE") ||
             sql.toUpperCase().contains("'1'='1'"))) {
            return true;
        }
        return false;
    }

    /**
     * 检测恒等式条件
     * 例如: WHERE 1=1
     */
    private boolean detectAlwaysTrueCondition(String sql) {
        String upperSql = sql.toUpperCase();
        // 检测 WHERE 1=1 或类似恒等式
        if (upperSql.contains("WHERE 1=1") || 
            upperSql.contains("WHERE 1 = 1") ||
            upperSql.contains("WHERE TRUE") ||
            upperSql.contains("WHERE 1")) {
            // 检查是否有其他条件
            int whereIndex = upperSql.indexOf("WHERE");
            if (whereIndex >= 0) {
                String afterWhere = upperSql.substring(whereIndex + 5).trim();
                // 如果WHERE后面直接跟恒等式，没有其他条件，可能是攻击
                if (afterWhere.startsWith("1=1") || 
                    afterWhere.startsWith("1 = 1") ||
                    afterWhere.startsWith("TRUE")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查是否在允许的关键字中
     */
    private boolean isAllowedKeyword(String sql) {
        String upperSql = sql.toUpperCase();
        for (String keyword : ALLOWED_KEYWORDS) {
            if (upperSql.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    /**
     * SQL注入异常
     */
    public static class SqlInjectException extends RuntimeException {
        public SqlInjectException(String message) {
            super(message);
        }
    }
}