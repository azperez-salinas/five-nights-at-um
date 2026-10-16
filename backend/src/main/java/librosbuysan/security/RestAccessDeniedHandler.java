package librosbuysan.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * RS27: manejador de acceso denegado para respuestas REST. Cuando un usuario
 * autenticado intenta acceder a una funcionalidad no habilitada para su rol
 * (o bloqueada por defecto con denyAll()), responde 403 con mensaje uniforme.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final AuthorizationAuditLogger auditLogger;

    public RestAccessDeniedHandler(AuthorizationAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        // RS23: rol sin permiso para la ruta (o ruta bloqueada por denyAll)
        auditLogger.denied(request, "FORBIDDEN_ROLE", HttpServletResponse.SC_FORBIDDEN);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"Acceso denegado\"}");
    }
}
