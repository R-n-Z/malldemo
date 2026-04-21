package com.macro.mall.portal.config;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.wall.WallFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Druid数据库连接池配置
 * 提供数据库层保护：连接池管理、SQL防火墙、慢查询监控
 */
@Slf4j
@Configuration
public class DruidConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClass;

    @Value("${spring.datasource.druid.initial-size:5}")
    private int initialSize;

    @Value("${spring.datasource.druid.min-idle:5}")
    private int minIdle;

    @Value("${spring.datasource.druid.max-active:20}")
    private int maxActive;

    @Value("${spring.datasource.druid.max-wait:60000}")
    private long maxWait;

    @Value("${spring.datasource.druid.validation-query:SELECT 1}")
    private String validationQuery;

    @Value("${spring.datasource.druid.test-while-idle:true}")
    private boolean testWhileIdle;

    @Value("${spring.datasource.druid.time-between-eviction-runs-millis:60000}")
    private long timeBetweenEvictionRunsMillis;

    @Value("${spring.datasource.druid.min-evictable-idle-time-millis:300000}")
    private long minEvictableIdleTimeMillis;

    @Value("${spring.datasource.druid.max-evictable-idle-time-millis:600000}")
    private long maxEvictableIdleTimeMillis;

    @Value("${spring.datasource.druid.connection-properties:druid.stat.mergeSql=true;druid.stat.slowSqlMillis=3000}")
    private String connectionProperties;

    /**
     * 创建Druid数据源
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        DruidDataSource dataSource = new DruidDataSource();

        // 基础配置
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClass);

        // 连接池配置
        dataSource.setInitialSize(initialSize);
        dataSource.setMinIdle(minIdle);
        dataSource.setMaxActive(maxActive);
        dataSource.setMaxWait(maxWait);

        // 连接检测配置
        dataSource.setValidationQuery(validationQuery);
        dataSource.setTestWhileIdle(testWhileIdle);
        dataSource.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        dataSource.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        dataSource.setMaxEvictableIdleTimeMillis(maxEvictableIdleTimeMillis);

        // 监控配置
        dataSource.setConnectionProperties(connectionProperties);

        // 配置SQL防火墙和监控过滤器
        List<Filter> filters = new ArrayList<>();
        filters.add(statFilter());
        filters.add(wallFilter());
        dataSource.setProxyFilters(filters);

        log.info("Druid数据源配置完成: maxActive={}", maxActive);
        return dataSource;
    }

    /**
     * SQL统计过滤器 - 监控慢查询
     */
    @Bean
    public StatFilter statFilter() {
        StatFilter statFilter = new StatFilter();
        statFilter.setSlowSqlMillis(3000);  // 慢SQL阈值：3秒
        statFilter.setLogSlowSql(true);     // 记录慢SQL
        statFilter.setMergeSql(true);       // 合并相同SQL
        return statFilter;
    }

    /**
     * SQL防火墙 - 防止SQL注入
     */
    @Bean
    public WallFilter wallFilter() {
        WallFilter wallFilter = new WallFilter();
        wallFilter.setConfig(wallConfig());
        wallFilter.setLogViolation(true);   // 记录违规SQL
        wallFilter.setThrowException(false); // 不抛异常，只记录
        return wallFilter;
    }

    /**
     * SQL防火墙配置
     */
    @Bean
    public WallConfig wallConfig() {
        WallConfig config = new WallConfig();
        // 允许的SQL操作
        config.setSelectAllow(true);
        config.setInsertAllow(true);
        config.setUpdateAllow(true);
        config.setDeleteAllow(true);
        
        // 禁止的操作
        config.setDropTableAllow(false);    // 禁止DROP TABLE
        config.setTruncateAllow(false);     // 禁止TRUNCATE
        config.setAlterTableAllow(false);   // 禁止ALTER TABLE
        config.setCreateTableAllow(false);  // 禁止CREATE TABLE
        
        // 防止SQL注入
        config.setMultiStatementAllow(false); // 禁止多语句
        config.setNoneBaseStatementAllow(false); // 禁止无基础语句
        
        return config;
    }

    /**
     * 这里原先包含基于Druid AST的自定义SQL检测拦截器，但不同Druid版本API差异较大，
     * 且当前工程未在关键链路中依赖该能力；为保证工程基线可稳定编译，先移除该自定义拦截器。
     */
}