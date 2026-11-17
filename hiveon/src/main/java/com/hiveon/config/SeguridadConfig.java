package com.hiveon.config;

import com.hiveon.model.Cuentas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
    UserDetailsService usuarios(Cuentas cuentas) {
        return correo -> cuentas.buscarCredenciales(correo)
                .map(cuenta -> User.withUsername(cuenta.correo())
                        .password(cuenta.hash())
                        .roles(cuenta.rol())
                        .disabled(!cuenta.habilitada())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Cuenta no disponible"));
    }
}
