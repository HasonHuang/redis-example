package com.hason.redis.config;

import org.beetl.core.resource.WebAppResourceLoader;
import org.beetl.ext.spring.BeetlGroupUtilConfiguration;
import org.beetl.ext.spring.BeetlSpringViewResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

/**
 * Beetl 模板引擎配置
 *
 * @author Huanghs
 * @since 2.0
 * @date 2017/3/18
 */
@Configuration
public class BeetlTemplateConfig {

    // 配置 Beetl
    @Bean(initMethod = "init", name = "beetlConfig")
    public BeetlGroupUtilConfiguration getBeetlGroupUtilConfiguration() {
        BeetlGroupUtilConfiguration configuration = new BeetlGroupUtilConfiguration();
        // 获取资源解析器
        ResourcePatternResolver patternResolver = ResourcePatternUtils
                .getResourcePatternResolver(new DefaultResourceLoader());

        try {

            // 获取模板文件所在的根目录
            String root = patternResolver.getResource("classpath:templates").getFile().getPath();
            WebAppResourceLoader webAppResourceLoader = new WebAppResourceLoader(root);
            // 设置资源加载器
            configuration.setResourceLoader(webAppResourceLoader);
            // 设置配置文件
            configuration.setConfigFileResource(patternResolver.getResource("classpath:META-INF/beetl.properties"));

            return configuration;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 设置 Spring MVC 视图解析器
    @Bean(name = "beetlViewResolver")
    public BeetlSpringViewResolver getBeetlSpringViewResolver(
            @Qualifier(value = "beetlConfig") BeetlGroupUtilConfiguration configuration) {
        BeetlSpringViewResolver viewResolver = new BeetlSpringViewResolver();
        viewResolver.setContentType("text/html;charset=UTF-8");  // 页面编码
        viewResolver.setSuffix(".btl");  // 视图的后缀
        viewResolver.setOrder(0);  // ViewResolver 中的执行顺序
        viewResolver.setConfig(configuration);  // 使用 Beetl 配置
        return viewResolver;
    }
}
