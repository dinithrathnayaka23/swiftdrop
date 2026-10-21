package com.swiftdrop.common.security;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.jwt.JwtAuthenticationFilter;
import com.swiftdrop.common.security.jwt.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security baseline.
 *
 * <ul>
 *   <li>Public: auth endpoints, public tracking, ping, actuator health, Swagger (dev only).</li>
 *   <li>Everything else requires a valid bearer token.</li>
 *   <li>Fine-grained rules belong on controllers/services via {@code @PreAuthorize}.</li>
 * </ul>
 *
 * <p>Google OAuth: the {@code google} client registration is configured in application.yml.
 * Wiring {@code oauth2Login()} into this chain (and issuing our own JWT afterwards) is
 * left to the identity module owner.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ROUTES = {
            ApiPaths.AUTH + "/**",
            ApiPaths.PUBLIC_TRACKING + "/**",
            ApiPaths.HEALTH,
            "/actuator/health",
            "/actuator/health/**",
            "/error"
    };

    private static final String[] API_DOC_ROUTES = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   SecurityErrorHandler errorHandler,
                                                   @Value("${swiftdrop.security.public-api-docs:false}")
                                                   boolean publicApiDocs) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers(PUBLIC_ROUTES).permitAll();
                    if (publicApiDocs) {
                        auth.requestMatchers(API_DOC_ROUTES).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
