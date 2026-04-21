package com.macro.mall.portal.config;

import com.macro.mall.portal.component.SqlInjectInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * MyBatis SQL注入防护配置
 * 自动注册SQL注入防护拦截器
 */
@Configuration
public class MybatisSqlInjectConfig {

    @Autowired
    private List<SqlSessionFactory> sqlSessionFactories;

    @PostConstruct
    public void init() {
        SqlInjectInterceptor interceptor = new SqlInjectInterceptor();
        
        for (SqlSessionFactory sqlSessionFactory : sqlSessionFactories) {
            org.apache.ibatis.session.Configuration configuration = sqlSessionFactory.getConfiguration();
            
            // 检查是否已添加拦截器
            if (!hasInterceptor(configuration, SqlInjectInterceptor.class.getName())) {
                configuration.addInterceptor(interceptor);
                SqlInjectInterceptor.LOGGER.info("SQL注入防护拦截器已注册");
            }
        }
    }

    /**
     * 检查是否已添加指定拦截器
     */
    private boolean hasInterceptor(org.apache.ibatis.session.Configuration configuration, String interceptorClassName) {
        try {
            List<org.apache.ibatis.plugin.Interceptor> interceptors = configuration.getInterceptors();
            for (org.apache.ibatis.plugin.Interceptor interceptor : interceptors) {
                if (interceptor.getClass().getName().equals(interceptorClassName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            SqlInjectInterceptor.LOGGER.error("检查拦截器失败", e);
        }
        return false;
    }
}