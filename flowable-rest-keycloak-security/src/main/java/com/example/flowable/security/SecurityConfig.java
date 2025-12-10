package com.example.flowable.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(
                    "/service/**",
                    "/bpmn-api/**",
                    "/cmmn-api/**",
                    "/dmn-api/**",
                    "/form-api/**",
                    "/content-api/**"
                ).authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        http.addFilterAfter(jwtLoggingFilter(), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> combined = new ArrayList<>();
            combined.addAll(authoritiesConverter.convert(jwt));
            combined.addAll(extractRealmRoles(jwt));
            combined.addAll(extractResourceRoles(jwt));
            return combined;
        });
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    @Bean
    public RequestAuthenticationLoggingFilter jwtLoggingFilter() {
        return new RequestAuthenticationLoggingFilter();
    }

    private Collection<? extends GrantedAuthority> extractRealmRoles(org.springframework.security.oauth2.jwt.Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof Collection<?> roleCollection)) {
            return List.of();
        }
        return roleCollection.stream()
            .map(Object::toString)
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toSet());
    }

    private Collection<? extends GrantedAuthority> extractResourceRoles(org.springframework.security.oauth2.jwt.Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess == null) {
            return List.of();
        }
        // Include roles for the authorized party (azp) and "account" if present.
        List<String> clientIds = new ArrayList<>();
        Optional.ofNullable(jwt.getClaimAsString("azp")).ifPresent(clientIds::add);
        clientIds.add("account");

        return resourceAccess.entrySet().stream()
            .filter(entry -> clientIds.contains(entry.getKey()))
            .flatMap(entry -> {
                Object value = entry.getValue();
                if (!(value instanceof Map<?, ?> clientMap)) {
                    return List.<String>of().stream();
                }
                Object roles = clientMap.get("roles");
                if (!(roles instanceof Collection<?> roleCollection)) {
                    return List.<String>of().stream();
                }
                return roleCollection.stream().map(Object::toString);
            })
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toSet());
    }
}
