package com.example.expensetracker.config;

import com.example.expensetracker.auth.AuthRoutes;
import com.example.expensetracker.auth.JwtAuthenticationFilter;
import com.example.expensetracker.auth.JwtProperties;
import com.example.expensetracker.auth.JwtService;
import com.example.expensetracker.common.exception.ApiErrorWriter;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, ApiErrorWriter errorWriter)
            throws Exception {
        configureAuthorization(http);
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.requestCache(AbstractHttpConfigurer::disable);
        configureSecurityErrors(http, errorWriter);
        configureStaticHeaders(http);
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        SecurityFilterChain filterChain = http.build();
        return filterChain;
    }

    private void configureAuthorization(HttpSecurity http) throws Exception {
        String[] publicStaticPaths = StaticWebSecurity.PUBLIC_PATHS.toArray(String[]::new);
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, AuthRoutes.REGISTER, AuthRoutes.LOGIN).permitAll()
                .requestMatchers(HttpMethod.GET, publicStaticPaths).permitAll()
                .requestMatchers(HttpMethod.HEAD, publicStaticPaths).permitAll()
                .requestMatchers(AuthRoutes.API_DOCS, AuthRoutes.SWAGGER_UI, AuthRoutes.SWAGGER_ENTRY).permitAll()
                .anyRequest().authenticated());
    }

    private void configureSecurityErrors(HttpSecurity http, ApiErrorWriter errorWriter) throws Exception {
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                        errorWriter.writeSecurityError(request, response, HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((request, response, exception) ->
                        errorWriter.writeSecurityError(request, response, HttpStatus.FORBIDDEN)));
    }

    private void configureStaticHeaders(HttpSecurity http) throws Exception {
        // Swagger has its own asset requirements; apply the strict app policy only to app resources.
        http.headers(headers -> headers
                .addHeaderWriter(StaticWebSecurity.contentSecurityPolicyWriter())
                .frameOptions(frameOptions -> frameOptions.deny())
                .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return passwordEncoder;
    }

    @Bean
    UserDetailsService userDetailsService() {
        // AuthService and Bearer JWTs handle authentication; prevent a generated default login password.
        UserDetailsService userDetailsService = new InMemoryUserDetailsManager();
        return userDetailsService;
    }

    @Bean
    Clock jwtClock() {
        Clock clock = Clock.systemUTC();
        return clock;
    }
}
