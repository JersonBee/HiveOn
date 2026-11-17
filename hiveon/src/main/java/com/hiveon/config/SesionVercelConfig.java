package com.hiveon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
@Profile("vercel")
@EnableJdbcHttpSession(tableName = "hiveon.spring_session")
class SesionVercelConfig {

    @Bean
    DefaultCookieSerializer cookieDeSesion() {
        DefaultCookieSerializer cookie = new DefaultCookieSerializer();
        cookie.setUseSecureCookie(true);
        cookie.setSameSite("Lax");
        return cookie;
    }
}
