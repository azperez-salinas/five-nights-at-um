package librosbuysan.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * RS11/R11: la identidad del consumidor que origino la operacion viaja con
 * ella por toda la cadena (filtros -> controller -> service -> repositorio).
 * Corre justo despues de JwtAuthenticationFilter y deja en el MDC de SLF4J el
 * requestId, el userId y el rol del token, asi cada linea de log de cualquier
 * capa queda asociada al consumidor original. Las decisiones de autorizacion
 * siguen tomandose con el principal del SecurityContext, nunca con datos del
 * request.
 *
 * Sin @Component por el mismo motivo que JwtAuthenticationFilter: se
 * instancia en SecurityConfig.
 */
public class RequestIdentityMdcFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_ROLE = "role";
    public static final String ANONYMOUS = "anonymous";

    // Solo se acepta un id entrante corto y sin caracteres de control: un
    // valor con CR/LF permitiria inyectar lineas falsas en los logs.
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = sanitizeRequestId(request.getHeader(REQUEST_ID_HEADER));
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            MDC.put(MDC_REQUEST_ID, requestId);
            MDC.put(MDC_USER_ID, currentUserId());
            MDC.put(MDC_ROLE, currentRole());
            filterChain.doFilter(request, response);
        } finally {
            // El hilo vuelve al pool: sin esto la identidad de este request
            // quedaria pegada al siguiente que atienda el mismo hilo.
            MDC.clear();
        }
    }

    static String sanitizeRequestId(String incoming) {
        if (incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    private static String currentUserId() {
        AuthenticatedUser principal = currentPrincipal();
        return principal == null ? ANONYMOUS : principal.id().toString();
    }

    // R18: un dueno con contrasena temporal opera con el permiso acotado
    // PWD_CHANGE, no con su rol real; se registra el permiso efectivo.
    private static String currentRole() {
        AuthenticatedUser principal = currentPrincipal();
        if (principal == null) {
            return ANONYMOUS;
        }
        return principal.passwordChangeRequired()
                ? JwtAuthenticationFilter.PASSWORD_CHANGE_ROLE
                : principal.role().name();
    }

    private static AuthenticatedUser currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
