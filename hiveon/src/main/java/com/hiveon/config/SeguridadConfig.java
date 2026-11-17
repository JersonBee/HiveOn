package com.hiveon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SeguridadConfig {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http) {
        return http
                .authorizeHttpRequests(acceso -> acceso
                        .requestMatchers("/", "/ingresar", "/css/**", "/img/**", "/fonts/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/apiarios").hasRole("APICULTOR")
                        .requestMatchers("/apiarios/nuevo").hasRole("APICULTOR")
                        .requestMatchers("/apiarios/*/produccion/**", "/apiarios/*/reportes",
                                "/apiarios/*/expedientes").hasRole("APICULTOR")
                        .requestMatchers("/apiarios", "/apiarios/**").hasAnyRole("APICULTOR", "OPERARIO", "TECNICO")
                        .requestMatchers("/lotes", "/lotes/**").hasRole("COMPRADOR")
                        .requestMatchers("/expedientes", "/expedientes/**").hasRole("INSPECTOR")
                        .requestMatchers("/administracion", "/administracion/**").hasRole("ADMINISTRADOR")
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage("/ingresar")
                        .loginProcessingUrl("/ingresar")
                        .defaultSuccessUrl("/inicio")
                        .failureUrl("/ingresar?error"))
                .logout(logout -> logout
                        .logoutUrl("/salir")
                        .logoutSuccessUrl("/ingresar?salida"))
                .build();
    }

    @Bean
    PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService usuarios(JdbcClient jdbc) {
        return correo -> jdbc.sql("""
                select correo, contrasena_hash, rol_codigo, habilitado
                from hiveon.usuarios
                where lower(correo) = lower(:correo)
                """)
                .param("correo", correo.strip())
                .query((fila, numero) -> User.withUsername(fila.getString("correo"))
                        .password(fila.getString("contrasena_hash"))
                        .roles(fila.getString("rol_codigo"))
                        .disabled(!fila.getBoolean("habilitado"))
                        .build())
                .optional()
                .orElseThrow(() -> new UsernameNotFoundException("Cuenta no disponible"));
    }
}
