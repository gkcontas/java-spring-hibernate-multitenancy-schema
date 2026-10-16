package com.gkcontas.multitenancy.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the tenant of the request into the context and takes it out again. The
 * {@code finally} is not decoration: the request thread goes back to the
 * container's pool, so a context left behind becomes the tenant of the next
 * request served by that thread.
 */
@Component
@Order(TenantFilter.ORDER)
public class TenantFilter extends OncePerRequestFilter {

    public static final int ORDER = -100;
    public static final String HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String tenantId = request.getHeader(HEADER);
        try {
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.set(tenantId.trim());
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
