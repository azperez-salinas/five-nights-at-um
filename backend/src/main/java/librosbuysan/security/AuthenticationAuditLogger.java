package librosbuysan.security;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * RS32/R32: registro de eventos de autenticacion (logins exitosos, fallidos y
 * cierres de sesion) en el logger AUDIT para su almacenamiento protegido en un
 * sistema separado.
 *
 * Sigue el mismo formato estructurado key=value y sanitizacion anti-log-injection
 * que AuthorizationAuditLogger (RS23). Nunca registra contraseñas ni secretos.
 */
@Component
public class AuthenticationAuditLogger {

    private static final Logger audit = LoggerFactory.getLogger(AuthorizationAuditLogger.LOGGER_NAME);
    private static final int MAX_FIELD_CHARS = 100;

    public void loginSuccess(String username, String role) {
        audit.info(line("SUCCESS", "-", 200, username, role));
    }

    public void loginFailed(String username, String reason) {
        audit.warn(line("FAILED", reason, 401, username, "anonymous"));
    }

    public void logout(Long userId) {
        audit.info("event=AUTHN outcome=LOGOUT reason=- status=204 user="
                + (userId != null ? userId : "anonymous")
                + " role=authenticated method=POST resource=/api/auth/logout requestId="
                + sanitize(MDC.get(RequestIdentityMdcFilter.MDC_REQUEST_ID))
                + " ts=" + Instant.now());
    }

    private static String line(String outcome, String reason, int status, String username, String role) {
        return "event=AUTHN outcome=" + outcome
                + " reason=" + reason
                + " status=" + status
                + " user=" + sanitize(username)
                + " role=" + sanitize(role)
                + " method=POST"
                + " resource=/api/auth/login"
                + " requestId=" + sanitize(MDC.get(RequestIdentityMdcFilter.MDC_REQUEST_ID))
                + " ts=" + Instant.now();
    }

    private static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        String truncated = value.length() > MAX_FIELD_CHARS ? value.substring(0, MAX_FIELD_CHARS) + "..." : value;
        StringBuilder safe = new StringBuilder(truncated.length());
        for (char c : truncated.toCharArray()) {
            safe.append(Character.isISOControl(c) || Character.isWhitespace(c) || c == '=' ? '_' : c);
        }
        return safe.toString();
    }
}
