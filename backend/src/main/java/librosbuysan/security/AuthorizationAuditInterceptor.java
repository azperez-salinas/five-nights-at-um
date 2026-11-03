package librosbuysan.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * RS23/R23: registra la decision de autorizacion de los requests que pasaron
 * las reglas de rol de SecurityConfig y llegaron a un controller. Los 401 y
 * 403 de esas reglas no llegan aca: los registran RestAuthenticationEntryPoint
 * y RestAccessDeniedHandler.
 *
 * - Solo requests autenticados: login/registro y el catalogo consultado sin
 *   sesion no son decisiones de autorizacion sobre un consumidor.
 * - Se registran TODOS los metodos, incluidas las lecturas (GET/HEAD).
 *   fix/R23 (DS-75): antes se omitian los GET protegidos (/api/users/me,
 *   /mine, /libreria, /metricas, /favorites) y un acceso permitido al perfil
 *   no dejaba rastro en la auditoria. R23 pide poder identificar las
 *   operaciones de acceso permitidas y rechazadas, y leer datos propios
 *   (por ejemplo el perfil) es un acceso permitido que tiene que quedar
 *   registrado con usuario, operacion, recurso, resultado y fecha.
 * - Solo se excluye OPTIONS: es el preflight de CORS que arma el navegador,
 *   no una operacion del consumidor.
 * - RS20/R6: el 404 de un recurso ajeno es una denegacion real (se responde
 *   igual que "no existe" para no revelar nada); aca se registra como DENIED.
 *   El cliente recibe exactamente la misma respuesta que antes.
 *
 * Se registra a si mismo como interceptor para no sumar otra clase de
 * configuracion.
 */
@Component
public class AuthorizationAuditInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final String PREFLIGHT_METHOD = "OPTIONS";

    private final AuthorizationAuditLogger auditLogger;

    public AuthorizationAuditInterceptor(AuthorizationAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        if (PREFLIGHT_METHOD.equals(request.getMethod()) || !isAuthenticated()) {
            return;
        }
        int status = ex != null ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
        switch (status) {
            case HttpServletResponse.SC_NOT_FOUND -> auditLogger.denied(request, "NOT_OWNER_OR_NOT_FOUND", status);
            case HttpServletResponse.SC_UNAUTHORIZED -> auditLogger.denied(request, "UNAUTHENTICATED", status);
            case HttpServletResponse.SC_FORBIDDEN -> auditLogger.denied(request, "FORBIDDEN", status);
            // La autorizacion se concedio; un 400/409/500 posterior es de
            // validacion o del negocio, no de control de acceso.
            default -> auditLogger.allowed(request, status);
        }
    }

    private static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser;
    }
}
