package fu.fa26.sep490.g58.otcms_be.common.web;

import tools.jackson.databind.ObjectMapper;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ProductionReadinessGateInterceptor implements HandlerInterceptor {
    private final ProductionReadinessGate gate;
    private final ObjectMapper mapper;

    public ProductionReadinessGateInterceptor(ProductionReadinessGate gate, ObjectMapper mapper) {
        this.gate = gate;
        this.mapper = mapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!gate.isClosed() || !gate.protects(request.getMethod(), path)) return true;
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), ErrorResponse.of("PRODUCTION_READINESS_GATE_CLOSED",
                "Production command endpoints are disabled until readiness evidence is valid",
                CorrelationIdFilter.current(request)));
        return false;
    }
}
