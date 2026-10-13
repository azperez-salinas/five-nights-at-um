package librosbuysan.library;

import static librosbuysan.support.HttpAssertions.assertThrowsStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import librosbuysan.library.entity.Library;
import librosbuysan.library.repository.LibraryRepo;
import librosbuysan.library.service.LibraryService;
import librosbuysan.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * F15: para recursos privados el backend verifica que el usuario autenticado
 * sea el propietario. Aca, la libreria es privada de su dueno.
 */
@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {

    private static final Long DUENO_ID = 1L;
    private static final Long OTRO_DUENO_ID = 2L;
    private static final Long LIBRARY_ID = 50L;

    @Mock private LibraryRepo libraryRepo;

    private LibraryService service;
    private Library libreria;

    @BeforeEach
    void setUp() {
        service = new LibraryService(libraryRepo);
        User dueno = User.dueno("dueno", "d@test.local", "hash");
        ReflectionTestUtils.setField(dueno, "id", DUENO_ID);
        libreria = new Library(dueno, "Libreria Central");
    }

    @Test
    void elDuenoPuedeDarDeBajaSuPropiaLibreria() {
        when(libraryRepo.findById(LIBRARY_ID)).thenReturn(Optional.of(libreria));

        service.disableOwnLibrary(DUENO_ID, LIBRARY_ID);

        assertThat(libreria.isEnabled()).isFalse();
        verify(libraryRepo).save(libreria);
    }

    @Test
    void unUsuarioQueNoEsElDuenoRecibe404YLaLibreriaSigueHabilitada() {
        when(libraryRepo.findById(LIBRARY_ID)).thenReturn(Optional.of(libreria));

        assertThrowsStatus(HttpStatus.NOT_FOUND, () -> service.disableOwnLibrary(OTRO_DUENO_ID, LIBRARY_ID));

        assertThat(libreria.isEnabled()).isTrue();
        verify(libraryRepo, never()).save(any());
    }

    @Test
    void libreriaInexistenteYAjenaRespondenIgual() {
        when(libraryRepo.findById(LIBRARY_ID)).thenReturn(Optional.of(libreria));
        when(libraryRepo.findById(999L)).thenReturn(Optional.empty());

        String ajena = mensajeDe(() -> service.disableOwnLibrary(OTRO_DUENO_ID, LIBRARY_ID));
        String inexistente = mensajeDe(() -> service.disableOwnLibrary(OTRO_DUENO_ID, 999L));

        assertThat(ajena).isEqualTo(inexistente);
    }

    @Test
    void darDeBajaUnaLibreriaYaDeshabilitadaEs404() {
        libreria.disable();
        when(libraryRepo.findById(LIBRARY_ID)).thenReturn(Optional.of(libreria));

        assertThrowsStatus(HttpStatus.NOT_FOUND, () -> service.disableOwnLibrary(DUENO_ID, LIBRARY_ID));

        verify(libraryRepo, never()).save(any());
    }

    @Test
    void getOwnLibraryBuscaSoloPorElIdDelUsuarioAutenticado() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(DUENO_ID)).thenReturn(Optional.of(libreria));

        assertThat(service.getOwnLibrary(DUENO_ID).nombre()).isEqualTo("Libreria Central");
        verify(libraryRepo).findByDuenoIdAndEnabledTrue(DUENO_ID);
    }

    @Test
    void getOwnLibraryDeUnUsuarioSinLibreriaEs404() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(OTRO_DUENO_ID)).thenReturn(Optional.empty());

        assertThrowsStatus(HttpStatus.NOT_FOUND, () -> service.getOwnLibrary(OTRO_DUENO_ID));
    }

    private static String mensajeDe(Runnable call) {
        try {
            call.run();
            throw new AssertionError("se esperaba ResponseStatusException");
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return e.getReason();
        }
    }
}