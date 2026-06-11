package com.syncerp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de segurança do SyncERP (v1).
 *
 * Conforme o escopo, a v1 opera sem autenticação na API interna.
 * Esta classe libera todos os endpoints e desativa proteções que não
 * fazem sentido numa API de máquina (CSRF, sessão, login form).
 *
 * IMPORTANTE: o spring-security-oauth2-client presente no projeto é usado
 * apenas como CLIENTE para autenticar contra a API do Bling (Fase 2),
 * de forma programática (WebClient + OAuth2AuthorizedClientManager).
 * Por isso NÃO ativamos oauth2Login() aqui — isso evita que o Spring
 * Security tente redirecionar requisições para uma tela de login.
 *
 * ATENÇÃO: como tudo está liberado (anyRequest().permitAll()), se a
 * porta 8080 for exposta diretamente à internet, a API ficará aberta.
 * Recomendado manter atrás de rede interna / proxy reverso.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // API de máquina: sem CSRF (não há sessão/cookie de navegador a proteger)
            .csrf(csrf -> csrf.disable())
            // Sem estado de sessão — cada requisição é independente
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Libera tudo (v1 sem auth, conforme escopo)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/webhooks/**",      // receptor de webhooks Bling
                    "/cnpjs/**",         // gestão de contas
                    "/eventos/**",       // histórico, falhas, reprocessamento
                    "/actuator/**",      // health + metrics
                    "/swagger-ui/**",    // Swagger UI
                    "/swagger-ui.html",
                    "/v3/api-docs/**"    // OpenAPI docs
                ).permitAll()
                .anyRequest().permitAll()
            )
            // Desativa mecanismos de login interativo (não usamos na v1)
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
