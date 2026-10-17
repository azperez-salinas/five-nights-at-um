package librosbuysan.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * RS11: el requestId entrante se sanitiza en el propio filtro, sin depender
 * de que el firewall o el contenedor ya hayan rechazado los saltos de linea.
 */
class RequestIdentityMdcFilterTest {

    @Test
    void unIdSeguroSeConserva() {
        assertThat(RequestIdentityMdcFilter.sanitizeRequestId("abc-123_X.9")).isEqualTo("abc-123_X.9");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc\r\nFAKE", "abc\nx", "abc\tx", "con espacio", ""})
    void unIdInseguroSeReemplazaPorUnUuid(String inseguro) {
        String id = RequestIdentityMdcFilter.sanitizeRequestId(inseguro);
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
    }

    @Test
    void unIdNuloODemasiadoLargoSeReemplazaPorUnUuid() {
        assertThat(UUID.fromString(RequestIdentityMdcFilter.sanitizeRequestId(null))).isNotNull();
        assertThat(UUID.fromString(RequestIdentityMdcFilter.sanitizeRequestId("a".repeat(65)))).isNotNull();
    }
}
