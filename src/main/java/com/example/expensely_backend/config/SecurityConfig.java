package com.example.expensely_backend.config;

import com.example.expensely_backend.utils.CustomAuthEntryPoint;
import com.example.expensely_backend.utils.JwtAuthFilter;
import lombok.Data;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Data
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CustomAuthEntryPoint customAuthEntryPoint;
    String allowedOrigins = System.getenv("ALLOWED_ORIGINS");
    String[] origins = allowedOrigins != null ? allowedOrigins.split(",") : new String[]{};

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, CustomAuthEntryPoint customAuthEntryPoint) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.customAuthEntryPoint = customAuthEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors().and()
                .csrf().disable()
                .exceptionHandling()
                .authenticationEntryPoint(customAuthEntryPoint)
                .and()
                .authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers("/api/users/register", "/api/users/login",
                                        "/ping", "/api/users/verify-oauth-login", "/api/users" +
                                                "/refresh").permitAll() // Allow public access to registration and login
                                .anyRequest().authenticated() // All other requests require authentication
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        .allowedHeaders("Content-Type", "Authorization", "X-Requested-With")
                        .exposedHeaders("Set-Cookie")
                        .allowCredentials(true);
            }
        };
    }
}

//export $(cat .env | xargs) && ./gradlew bootRun