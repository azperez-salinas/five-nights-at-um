package librosbuysan.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Los services de este proyecto comunican errores con ResponseStatusException.
 * Este helper evita repetir en cada test el mismo bloque para chequear el
 * status HTTP.
 */
public final class HttpAssertions {

    private HttpAssertions() {
    }

    public static void assertThrowsStatus(HttpStatus expected, ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(expected.value()));
    }
}
