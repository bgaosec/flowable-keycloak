package com.example.flowable.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Debug-only logging filter to trace authentication outcomes without exposing tokens.
 */
public class RequestAuthenticationLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestAuthenticationLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        filterChain.doFilter(request, response);

        if (!LOGGER.isDebugEnabled()) {
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            LOGGER.debug("Auth TRACE uri={} method={} status={} auth=null", request.getRequestURI(), request.getMethod(), response.getStatus());
            return;
        }

        String authorities = auth.getAuthorities().stream()
            .map(Object::toString)
            .collect(Collectors.joining(","));

        LOGGER.debug("Auth TRACE uri={} method={} status={} principal={} authClass={} authorities={}",
            request.getRequestURI(),
            request.getMethod(),
            response.getStatus(),
            auth.getName(),
            auth.getClass().getSimpleName(),
            authorities);
    }
}
