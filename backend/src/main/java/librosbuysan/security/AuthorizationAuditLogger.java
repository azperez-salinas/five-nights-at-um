package librosbuysan.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * RS23/R23: registro de las decisiones de autorizacion (permitidas y
 * rechazadas). Usa un logger propio ("AUDIT") para que RS32 pueda mandarlo a
 * un appender separado y protegido sin tocar este codigo.
 *
 * Una linea key=value por decision: consumidor (user/role), operacion
 * (method), recurso (resource), resultado (outcome/reason/status), requestId y
 * fecha/hora UTC. El consumidor sale del MDC que carga RequestIdentityMdcFilter
 * (RS11) a partir del principal del token.
 *
 * Nunca se registran tokens, el header Authorization, bodies, contrasenas ni
 * emails: solo ids, rol, metodo y path.
 */
@Component
public class AuthorizationAuditLogger {

    public static final String LOGGER_NAME = "AUDIT";

    private static final Logger audit = LoggerFactory.getLogger(LOGGER_NAME);

    // Largo maximo del path registrado: evita lineas gigantes a proposito
    private static final int MAX_RESOURCE_CHARS = 200;

    public void allowed(HttpServletRequest request, int status) {
        audit.info(line("ALLOWED", "-", status, request));
    }

    public void denied(HttpServletRequest request, String reason, int status) {
        audit.warn(line("DENIED", reason, status, request));
    }

    private static String line(String outcome, String reason, int status, HttpServletRequest request) {
        return "event=AUTHZ outcome=" + outcome
                + " reason=" + reason
                + " status=" + status
                + " user=" + mdcOrAnonymous(RequestIdentityMdcFilter.MDC_USER_ID)
                + " role=" + mdcOrAnonymous(RequestIdentityMdcFilter.MDC_ROLE)
                + " method=" + sanitize(request.getMethod())
                + " resource=" + sanitize(request.getRequestURI())
                + " requestId=" + sanitize(MDC.get(RequestIdentityMdcFilter.MDC_REQUEST_ID))
                + " ts=" + Instant.now();
    }

    private static String mdcOrAnonymous(String key) {
        String value = MDC.get(key);
        return value == null ? RequestIdentityMdcFilter.ANONYMOUS : sanitize(value);
    }

    // Anti log injection: sin caracteres de control (CR/LF/tab), espacios ni
    // '=', que romperian la linea o permitirian colar un key=value falso
    // (ej. "outcome=ALLOWED" dentro del path), y con largo acotado.
    static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        String truncated = value.length() > MAX_RESOURCE_CHARS ? value.substring(0, MAX_RESOURCE_CHARS) + "..." : value;
        StringBuilder safe = new StringBuilder(truncated.length());
        for (char c : truncated.toCharArray()) {
            safe.append(Character.isISOControl(c) || Character.isWhitespace(c) || c == '=' ? '_' : c);
        }
        return safe.toString();
    }
}
