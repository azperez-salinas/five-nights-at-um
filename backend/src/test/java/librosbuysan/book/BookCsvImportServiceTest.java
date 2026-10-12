
package librosbuysan.book;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import librosbuysan.library.Library;
import librosbuysan.library.LibraryRepo;

@ExtendWith(MockitoExtension.class)
class BookCsvImportServiceTest {

    @Mock
    private BookRepo bookRepo;

    @Mock
    private LibraryRepo libraryRepo;

    @Mock
    private Library library;

    @InjectMocks
    private BookCsvImportService service;

    private static final String HEADER =
            "titulo;autor;isbn;cantidad_paginas;descripcion;anio\n";

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile(
                "file",
                "books.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    void importsAllValidBooks() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.of(library));

        String content = HEADER
                + "\"Cien años de soledad\";"
                + "\"Gabriel García Márquez\";"
                + "9780307474728;417;"
                + "\"Novela sobre Macondo\";1967\n"
                + "\"El principito\";"
                + "\"Antoine de Saint-Exupéry\";"
                + "9780156012195;96;"
                + "\"Relato sobre la amistad\";1943\n";

        CsvImportResult result =
                service.importCsv(10L, csv(content));

        assertTrue(result.success());
        assertEquals(2, result.imported());
        assertTrue(result.errors().isEmpty());

        verify(bookRepo).saveAll(anyList());
    }

    @Test
    void doesNotSaveAnyBookIfOneRowIsInvalid() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.of(library));

        String content = HEADER
                + "\"Libro válido\";\"Autor\";"
                + "9780307474728;100;\"Descripción\";2000\n"
                + "\"Libro inválido\";\"Autor\";"
                + "123;100;\"Descripción\";2000\n";

        CsvImportResult result =
                service.importCsv(10L, csv(content));

        assertFalse(result.success());
        assertEquals(0, result.imported());
        assertTrue(result.errors().stream()
                .anyMatch(error -> error.contains("Fila 3")));

        verify(bookRepo, never()).saveAll(anyList());
    }

    @Test
    void assignsEveryBookToAuthenticatedOwnersLibrary() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.of(library));

        String content = HEADER
                + "\"Libro A\";\"Autor A\";"
                + "9780307474728;100;\"Descripción A\";2000\n"
                + "\"Libro B\";\"Autor B\";"
                + "9780156012195;200;\"Descripción B\";2001\n";

        service.importCsv(10L, csv(content));

        ArgumentCaptor<List<Book>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(bookRepo).saveAll(captor.capture());

        List<Book> saved = captor.getValue();

        assertEquals(2, saved.size());
        assertSame(library, saved.get(0).getLibrary());
        assertSame(library, saved.get(1).getLibrary());
    }

    @Test
    void rejectsInvalidFileExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "books.txt",
                "text/plain",
                HEADER.getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.importCsv(10L, file)
        );

        verify(bookRepo, never()).saveAll(anyList());
    }

    @Test
    void rejectsMissingRequiredColumn() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.of(library));

        String content =
                "titulo;autor;isbn;cantidad_paginas;anio\n"
                        + "\"Libro\";\"Autor\";"
                        + "9780307474728;100;2000\n";

        assertThrows(
                ResponseStatusException.class,
                () -> service.importCsv(10L, csv(content))
        );

        verify(bookRepo, never()).saveAll(anyList());
    }

    @Test
    void rejectsOwnerWithoutEnabledLibrary() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResponseStatusException.class,
                () -> service.importCsv(10L, csv(HEADER))
        );

        verify(bookRepo, never()).saveAll(anyList());
    }

    @Test
    void rejectsEmptyCsv() {
        MockMultipartFile empty = new MockMultipartFile(
                "file",
                "books.csv",
                "text/csv",
                new byte[0]
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.importCsv(10L, empty)
        );

        verify(bookRepo, never()).saveAll(anyList());
    }

    @Test
    void rejectsIsbnWithInvalidCheckDigit() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.of(library));

        String content = HEADER
                + "\"Libro\";\"Autor\";"
                + "9780307474729;100;\"Descripción\";2000\n";

        CsvImportResult result =
                service.importCsv(10L, csv(content));

        assertFalse(result.success());
        assertEquals(0, result.imported());
        assertTrue(result.errors().stream()
                .anyMatch(error -> error.contains("ISBN inválido")));

        verify(bookRepo, never()).saveAll(anyList());
    }

    @Test
    void rejectsZeroPages() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.of(library));

        String content = HEADER
                + "\"Libro\";\"Autor\";"
                + "9780307474728;0;\"Descripción\";2000\n";

        CsvImportResult result =
                service.importCsv(10L, csv(content));

        assertFalse(result.success());
        assertTrue(result.errors().stream()
                .anyMatch(error -> error.contains("cantidad_paginas")));

        verify(bookRepo, never()).saveAll(anyList());
    }

    @Test
    void rejectsFutureYear() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.of(library));

        int futureYear = java.time.LocalDate.now().getYear() + 1;

        String content = HEADER
                + "\"Libro\";\"Autor\";"
                + "9780307474728;100;\"Descripción\";"
                + futureYear + "\n";

        CsvImportResult result =
                service.importCsv(10L, csv(content));

        assertFalse(result.success());
        assertTrue(result.errors().stream()
                .anyMatch(error -> error.contains("anio")));

        verify(bookRepo, never()).saveAll(anyList());
    }

    @Test
    void rejectsEmptyDescription() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.of(library));

        String content = HEADER
                + "\"Libro\";\"Autor\";"
                + "9780307474728;100;;2000\n";

        CsvImportResult result =
                service.importCsv(10L, csv(content));

        assertFalse(result.success());
        assertTrue(result.errors().stream()
                .anyMatch(error -> error.contains("descripción")));

        verify(bookRepo, never()).saveAll(anyList());
    }

    @Test
    void rejectsTitleLongerThan255Characters() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.of(library));

        String longTitle = "A".repeat(256);

        String content = HEADER
                + "\"" + longTitle + "\";\"Autor\";"
                + "9780307474728;100;\"Descripción\";2000\n";

        CsvImportResult result =
                service.importCsv(10L, csv(content));

        assertFalse(result.success());
        assertTrue(result.errors().stream()
                .anyMatch(error -> error.contains("título")));

        verify(bookRepo, never()).saveAll(anyList());
    }

    @Test
    void rejectsFileLargerThan2Mb() {
        byte[] content = new byte[(2 * 1024 * 1024) + 1];

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "books.csv",
                "text/csv",
                content
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.importCsv(10L, file)
        );

        verify(bookRepo, never()).saveAll(anyList());
    }

    @Test
    void rejectsCsvWithoutBooks() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.of(library));

        CsvImportResult result =
                service.importCsv(10L, csv(HEADER));

        assertFalse(result.success());
        assertEquals(0, result.imported());

        verify(bookRepo, never()).saveAll(anyList());
    }

    @Test
    void acceptsSemicolonInsideQuotedDescription() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.of(library));

        String content = HEADER
                + "\"Libro\";\"Autor\";"
                + "9780307474728;100;"
                + "\"Descripción con ; punto y coma\";2000\n";

        CsvImportResult result =
                service.importCsv(10L, csv(content));

        assertTrue(result.success());
        assertEquals(1, result.imported());

        verify(bookRepo).saveAll(anyList());
    }

    @Test
    void ignoresAdditionalColumns() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(10L))
                .thenReturn(Optional.of(library));

        String content =
                HEADER.trim() + ";library_id;columna_extra\n"
                        + "\"Libro\";\"Autor\";"
                        + "9780307474728;100;\"Descripción\";2000;"
                        + "999;\"Ignorar\"\n";

        CsvImportResult result =
                service.importCsv(10L, csv(content));

        assertTrue(result.success());
        assertEquals(1, result.imported());

        verify(bookRepo).saveAll(anyList());
    }
}
