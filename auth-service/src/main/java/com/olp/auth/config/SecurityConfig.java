package com.olp.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    public SecurityConfig(OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler) {
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
      http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth ->
          auth
            .requestMatchers(
              "/actuator/**",
              "/auth/public/**",
              "/auth/login",
              "/auth/register",
              "/auth/forgot-password",
              "/auth/reset-password",
              "/auth/login/success",
              "/auth/validate",
              "/auth/me",
              "/auth/refresh",
              "/auth/profile",
              "/auth/password",
              "/auth/logout",
              "/oauth2/**",
              "/v3/api-docs/**",
              "/swagger-ui/**",
              "/swagger-ui.html"
            ).permitAll()
            .anyRequest().authenticated()
        )
        .oauth2Login(oauth -> oauth.successHandler(oAuth2LoginSuccessHandler));

      return http.build();
    }
}
