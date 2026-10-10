package librosbuysan.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * R3: la lista de revocados es lo que da efecto real al logout. Se prueba
 * sola, sin Spring: es una clase sin dependencias.
 */
class TokenRevocationServiceTest {

    private TokenRevocationService service;

    @BeforeEach
    void setUp() {
        service = new TokenRevocationService();
    }

    private static Instant enUnaHora() {
        return Instant.now().plusSeconds(3600);
    }

    @Test
    void unTokenQueNuncaSeRevocoNoEstaRevocado() {
        assertThat(service.isRevoked("jti-nunca-visto")).isFalse();
    }

    @Test
    void despuesDeRevocarElTokenQuedaRevocado() {
        service.revoke("jti-1", enUnaHora());

        assertThat(service.isRevoked("jti-1")).isTrue();
    }

    @Test
    void revocarUnTokenNoAfectaAOtrosTokens() {
        // Un mismo usuario puede tener dos sesiones (dos dispositivos): cerrar una no cierra la otra
        service.revoke("jti-celular", enUnaHora());

        assertThat(service.isRevoked("jti-celular")).isTrue();
        assertThat(service.isRevoked("jti-notebook")).isFalse();
    }

    @Test
    void revocarDosVecesElMismoTokenEsIdempotente() {
        service.revoke("jti-1", enUnaHora());
        service.revoke("jti-1", enUnaHora());

        assertThat(service.isRevoked("jti-1")).isTrue();
    }

    @Test
    void unTokenQueYaExpiroNoSeGuardaPorqueElFiltroYaLoRechazaPorExpiracion() {
        service.revoke("jti-viejo", Instant.now().minusSeconds(1));

        assertThat(service.isRevoked("jti-viejo")).isFalse();
    }

    @Test
    void cadaRevocacionLimpiaLasEntradasQueYaVencieron() {
        // Se inserta directo una entrada vencida: esperar a que venza de verdad haria el test lento y fragil
        Map<String, Instant> internas = revocadosInternos();
        internas.put("jti-vencido", Instant.now().minusSeconds(10));
        assertThat(service.isRevoked("jti-vencido")).as("precondicion: figura como revocado").isTrue();

        service.revoke("jti-nuevo", enUnaHora());

        assertThat(service.isRevoked("jti-vencido")).as("se purgo al revocar otro").isFalse();
        assertThat(service.isRevoked("jti-nuevo")).isTrue();
    }

    @Test
    void laLimpiezaNoBorraLosTokensTodaviaVigentes() {
        service.revoke("jti-vigente", enUnaHora());

        service.revoke("jti-otro", enUnaHora());

        assertThat(service.isRevoked("jti-vigente")).isTrue();
    }

    @Test
    void soportaRevocacionesConcurrentesSinPerderNinguna() throws Exception {
        int cantidad = 200;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tareas = new ArrayList<>();
            for (int i = 0; i < cantidad; i++) {
                String jti = "jti-" + i;
                tareas.add(() -> {
                    service.revoke(jti, enUnaHora());
                    return null;
                });
            }
            for (Future<Void> f : pool.invokeAll(tareas)) {
                f.get(); // propaga cualquier excepcion ocurrida en un hilo
            }
        } finally {
            pool.shutdownNow();
        }

        for (int i = 0; i < cantidad; i++) {
            assertThat(service.isRevoked("jti-" + i)).as("jti-" + i).isTrue();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Instant> revocadosInternos() {
        return (Map<String, Instant>) ReflectionTestUtils.getField(service, "revoked");
    }
}