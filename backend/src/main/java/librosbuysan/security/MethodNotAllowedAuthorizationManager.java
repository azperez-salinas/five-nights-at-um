package librosbuysan.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * R33/RS27: reemplaza al denyAll() del final de SecurityConfig sin dejar de
 * denegar todo.
 *
 * Un verbo sin regla explicita cae en esta regla antes de llegar a Spring MVC,
 * asi que la ruta existente (ej. PUT /api/books, que solo admite GET) se
 * respondia 403 en vez de 405. Aca se consulta el mismo RequestMappingHandlerMapping
 * que usa el DispatcherServlet: si la ruta existe pero no con ese verbo, se
 * guardan los metodos admitidos en la request para que RestAccessDeniedHandler
 * responda 405 con la cabecera Allow. La decision sigue siendo siempre denegar;
 * nunca se llega al controller.
 *
 * Las reglas por rol de arriba no pasan por aca: un rol sin permiso sobre una
 * ruta declarada sigue recibiendo 403 (RS1).
 */
@Component
public class MethodNotAllowedAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    static final String ALLOWED_METHODS_ATTRIBUTE = MethodNotAllowedAuthorizationManager.class.getName() + ".ALLOWED";

    private static final AuthorizationDecision DENY = new AuthorizationDecision(false);

    private final RequestMappingHandlerMapping handlerMapping;

    public MethodNotAllowedAuthorizationManager(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        Set<HttpMethod> allowed = supportedMethodsIfVerbNotMapped(request);
        if (allowed != null) {
            request.setAttribute(ALLOWED_METHODS_ATTRIBUTE, allowed);
        }
        return DENY;
    }

    private Set<HttpMethod> supportedMethodsIfVerbNotMapped(HttpServletRequest request) {
        // El handler mapping necesita el path ya parseado, y eso normalmente lo
        // hace el DispatcherServlet, que todavia no corrio. Se parsea aca y se
        // deja la request como estaba.
        RequestPath previous = ServletRequestPathUtils.hasParsedRequestPath(request)
                ? ServletRequestPathUtils.getParsedRequestPath(request)
                : null;
        ServletRequestPathUtils.parseAndCache(request);
        try {
            handlerMapping.getHandler(request);
            return null;
        } catch (HttpRequestMethodNotSupportedException ex) {
            return ex.getSupportedHttpMethods();
        } catch (Exception ex) {
            // Ruta inexistente, media type no soportado, etc.: queda el 403 de siempre
            return null;
        } finally {
            ServletRequestPathUtils.setParsedRequestPath(previous, request);
        }
    }
}
