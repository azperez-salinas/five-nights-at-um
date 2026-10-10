package librosbuysan.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import librosbuysan.auth.AuthDtos.AuthResponse;
import librosbuysan.auth.AuthDtos.LoginRequest;
import librosbuysan.auth.AuthDtos.RegisterDuenoRequest;
import librosbuysan.auth.AuthDtos.RegisterRequest;
import librosbuysan.book.BookDtos.BookSummary;
import librosbuysan.library.Library;
import librosbuysan.user.User;
import librosbuysan.user.UserDtos.ProfileResponse;
import librosbuysan.user.UserDtos.UpdateProfileRequest;
import org.junit.jupiter.api.Test;

/**
 * Los DTOs son la frontera de lo que sale/entra por la API (RS2, RS7, RS25):
 * se prueba que no filtren datos internos y que no impriman contrasenas.
 */
class BookDtosTest {

    private static List<String> campos(Class<? extends Record> tipo) {
        return Arrays.stream(tipo.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    @Test
    void bookSummaryCopiaLosCamposDelLibroYSoloElNombreDeLaLibreria() {
        Library library = new Library(User.dueno("dueno", "d@test.local", "hash"), "Libreria Central");
        Book book = new Book(library, "9789900000013", "Hamlet", "Shakespeare",
                LocalDate.of(1601, 1, 1), 342, "Un principe", "http://portada");

        BookSummary dto = BookSummary.from(book);

        assertThat(dto.isbn()).isEqualTo("9789900000013");
        assertThat(dto.titulo()).isEqualTo("Hamlet");
        assertThat(dto.autor()).isEqualTo("Shakespeare");
        assertThat(dto.fechaPublicacion()).isEqualTo(LocalDate.of(1601, 1, 1));
        assertThat(dto.cantidadPaginas()).isEqualTo(342);
        assertThat(dto.descripcion()).isEqualTo("Un principe");
        assertThat(dto.portadaUrl()).isEqualTo("http://portada");
        assertThat(dto.nombreLibreria()).isEqualTo("Libreria Central");
    }

    @Test
    void bookSummaryNoTieneCamposDeDuenoNiLibreriaCompleta() {
        assertThat(campos(BookSummary.class)).doesNotContain("library", "dueno", "user", "email", "passwordHash");
    }

    @Test
    void profileResponseNoTieneCamposDePassword() {
        assertThat(campos(ProfileResponse.class)).noneMatch(c -> c.toLowerCase().contains("password"));
    }

    @Test
    void profileResponseCopiaLosDatosDelUsuario() {
        User user = new User("pepe", "pepe@test.local", "hash");

        ProfileResponse dto = ProfileResponse.from(user);

        assertThat(dto.username()).isEqualTo("pepe");
        assertThat(dto.email()).isEqualTo("pepe@test.local");
        assertThat(dto.role()).isEqualTo(User.Role.COMPRADOR);
        assertThat(dto.createdAt()).isNotNull();
    }

    @Test
    void lasSolicitudesConContrasenaNuncaLaImprimenEnToString() {
        char[] clave = "SuperSecreta123".toCharArray();

        assertThat(new RegisterRequest("pepe", "p@test.local", clave).toString())
                .doesNotContain("SuperSecreta123").contains("[PROTECTED]");
        assertThat(new RegisterDuenoRequest("pepe", "p@test.local", clave, "Libreria").toString())
                .doesNotContain("SuperSecreta123").contains("[PROTECTED]");
        assertThat(new LoginRequest("pepe", clave).toString())
                .doesNotContain("SuperSecreta123").contains("[PROTECTED]");
        assertThat(new UpdateProfileRequest(clave, clave).toString())
                .doesNotContain("SuperSecreta123").contains("[PROTECTED]");
    }

    @Test
    void authResponseUsaBearerPorDefecto() {
        AuthResponse r = new AuthResponse("tok", 3600, "pepe", User.Role.COMPRADOR);

        assertThat(r.tokenType()).isEqualTo("Bearer");
        assertThat(r.token()).isEqualTo("tok");
        assertThat(r.expiresIn()).isEqualTo(3600);
        assertThat(r.username()).isEqualTo("pepe");
        assertThat(r.role()).isEqualTo(User.Role.COMPRADOR);
    }

    @Test
    void lasSolicitudesDeRegistroNoTienenCampoDeRolNiEnabled() {
        // RS25: el cliente no puede elegir su rol porque el DTO ni siquiera lo declara
        assertThat(campos(RegisterRequest.class)).doesNotContain("role", "enabled", "id");
        assertThat(campos(RegisterDuenoRequest.class)).doesNotContain("role", "enabled", "id");
    }
}
