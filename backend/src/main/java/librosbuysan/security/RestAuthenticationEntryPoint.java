package librosbuysan.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthorizationAuditLogger auditLogger;

    public RestAuthenticationEntryPoint(AuthorizationAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        // RS23: con header Authorization el token no se acepto (invalido,
        // expirado, revocado o de cuenta deshabilitada); sin header, no hubo
        // token. El valor del header nunca se registra.
        String reason = request.getHeader("Authorization") == null ? "NO_TOKEN" : "INVALID_TOKEN";
        auditLogger.denied(request, reason, HttpServletResponse.SC_UNAUTHORIZED);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"No autenticado\"}");
    }
}