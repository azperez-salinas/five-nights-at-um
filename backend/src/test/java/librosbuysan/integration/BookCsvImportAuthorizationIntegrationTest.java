package librosbuysan.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import librosbuysan.support.IntegrationTestBase;

class BookCsvImportAuthorizationIntegrationTest
        extends IntegrationTestBase {

    private MockMultipartFile validCsv() {
        return new MockMultipartFile(
                "file",
                "books.csv",
                "text/csv",
                (
                        "titulo;autor;isbn;cantidad_paginas;descripcion;anio\n"
                                + "\"Libro de prueba\";\"Autor de prueba\";"
                                + "9780307474728;100;\"Descripción de prueba\";2000\n"
                ).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    @Test
    void anonymousCannotImportBooks() throws Exception {
        mvc.perform(
                multipart("/api/books/import")
                        .file(validCsv())
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void buyerCannotImportBooks() throws Exception {
        mvc.perform(
                multipart("/api/books/import")
                        .file(validCsv())
                        .header(AUTHORIZATION, bearer(compradorA))
        ).andExpect(status().isForbidden());
    }

    @Test
    void ownerCanImportBooksIntoOwnLibrary() throws Exception {
        long initialCount = bookRepo.count();

        mvc.perform(
                        multipart("/api/books/import")
                                .file(validCsv())
                                .header(AUTHORIZATION, bearer(duenoCentral))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.imported").value(1));

        assertEquals(initialCount + 1, bookRepo.count());
    }

    @Test
    void ownerWithoutEnabledLibraryCannotImportBooks() throws Exception {
        mvc.perform(
                multipart("/api/books/import")
                        .file(validCsv())
                        .header(AUTHORIZATION, bearer(duenoBaja))
        ).andExpect(status().isNotFound());
    }
}