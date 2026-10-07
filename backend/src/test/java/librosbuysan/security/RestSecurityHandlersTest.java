package librosbuysan.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Las respuestas de 401 y 403 son parte del contrato de seguridad: deben ser
 * JSON uniforme y sin detalles internos (RS27, RS31).
 */
class RestSecurityHandlersTest {

    @Test
    void entryPointResponde401ConJsonUniforme() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint().commence(new MockHttpServletRequest(), response,
                new BadCredentialsException("detalle interno que no debe filtrarse"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"No autenticado\"}");
        assertThat(response.getContentAsString()).doesNotContain("detalle interno");
    }

    @Test
    void accessDeniedHandlerResponde403ConJsonUniforme() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler().handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("detalle interno que no debe filtrarse"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"Acceso denegado\"}");
        assertThat(response.getContentAsString()).doesNotContain("detalle interno");
    }
}
