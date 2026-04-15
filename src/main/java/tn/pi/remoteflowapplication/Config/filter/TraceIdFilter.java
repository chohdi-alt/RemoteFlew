package tn.pi.remoteflowapplication.config.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private final tn.pi.remoteflowapplication.infrastructure.security.ClientIpResolver clientIpResolver;

    public TraceIdFilter(tn.pi.remoteflowapplication.infrastructure.security.ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String traceId = UUID.randomUUID().toString();
        String ip = "unknown";

        if (request instanceof jakarta.servlet.http.HttpServletRequest httpRequest) {
            ip = clientIpResolver.resolve(httpRequest);
        }

        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put("ip", ip);

        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove("ip");
        }
    }
}
