package librosbuysan.library;

public final class LibraryDtos {

    private LibraryDtos() {
    }

    // RS2/RS7: solo id y nombre, nunca el dueno ni datos internos
    public record LibraryResponse(Long id, String nombre) {

        public static LibraryResponse from(Library library) {
            return new LibraryResponse(library.getId(), library.getNombre());
        }
    }
}
