package librosbuysan.book.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.ZoneId;


import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import librosbuysan.book.entity.Book;
import librosbuysan.book.dto.CsvImportResult;
import librosbuysan.book.repository.BookRepo;
import librosbuysan.library.entity.Library;
import librosbuysan.library.repository.LibraryRepo;

@Service
public class BookCsvImportService {

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;

    private static final List<String> EXPECTED_HEADERS = List.of(
            "titulo",
            "autor",
            "isbn",
            "cantidad_paginas",
            "descripcion",
            "anio"
    );

    private final BookRepo bookRepo;
    private final LibraryRepo libraryRepo;

    public BookCsvImportService(
            BookRepo bookRepo,
            LibraryRepo libraryRepo
    ) {
        this.bookRepo = bookRepo;
        this.libraryRepo = libraryRepo;
    }

    @Transactional
    public CsvImportResult importCsv(Long userId, MultipartFile file) {

        validateFile(file);

        Library library = libraryRepo
                .findByDuenoIdAndEnabledTrue(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Librería no encontrada"
                ));

        List<Book> pending = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (
                PushbackReader reader = new PushbackReader(
                        new InputStreamReader(
                                file.getInputStream(),
                                StandardCharsets.UTF_8
                        ),
                        1
                )
        ) {
            // Elimina el BOM UTF-8 si el CSV fue exportado por Excel.
            int firstChar = reader.read();

            if (firstChar != -1 && firstChar != '\uFEFF') {
                reader.unread(firstChar);
            }

            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setDelimiter(';')
                    .setIgnoreEmptyLines(true)
                    .get();

            try (CSVParser parser = format.parse(reader)) {

                var iterator = parser.iterator();

                if (!iterator.hasNext()) {
                    throw badRequest("El CSV está vacío");
                }

                CSVRecord headerRecord = iterator.next();
                Map<String, Integer> headerIndexes =
                        validateHeaders(headerRecord);

                int rowNumber = 1;

                while (iterator.hasNext()) {
                    CSVRecord theRecord = iterator.next();
                    rowNumber++;

                    if (theRecord.size() == 0) {
                        continue;
                    }

                    Map<String, String> row = new HashMap<>();

                    // Solamente leemos las columnas previstas.
                    // Las columnas extra nunca se utilizan.
                    for (String header : EXPECTED_HEADERS) {
                        int index = headerIndexes.get(header);

                        String value = index < theRecord.size()
                                ? theRecord.get(index).trim()
                                : "";

                        row.put(header, neutralize(value));
                    }

                    Book book = validateRow(
                            row,
                            rowNumber,
                            library,
                            errors
                    );

                    if (book != null) {
                        pending.add(book);
                    }
                }
            }

        } catch (IOException | IllegalArgumentException ignored) {
            throw badRequest("No se pudo procesar el archivo CSV");
        }

        // No se persiste ninguna fila si alguna fila es inválida.
        if (!errors.isEmpty()) {
            return new CsvImportResult(false, 0, errors);
        }

        if (pending.isEmpty()) {
            return new CsvImportResult(
                    false,
                    0,
                    List.of("El CSV no contiene libros para importar")
            );
        }

        // La operación está dentro de una transacción.
        // Un error de persistencia debe provocar rollback.
        bookRepo.saveAll(pending);

        return new CsvImportResult(
                true,
                pending.size(),
                List.of()
        );
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw badRequest("Debés seleccionar un archivo CSV");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("El archivo supera el máximo permitido de 2 MB");
        }

        String filename = file.getOriginalFilename();

        if (filename == null
                || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw badRequest("El archivo debe tener extensión .csv");
        }

        String contentType = file.getContentType();

        if (contentType == null
                || !(contentType.equalsIgnoreCase("text/csv")
                || contentType.equalsIgnoreCase(
                "application/vnd.ms-excel")
                || contentType.equalsIgnoreCase(
                "application/octet-stream"))) {
            throw badRequest("El archivo no tiene un tipo de contenido admitido");
        }
    }

    private Map<String, Integer> validateHeaders(CSVRecord header) {

        Map<String, Integer> indexes = new HashMap<>();

        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i)
                    .trim()
                    .toLowerCase(Locale.ROOT);

            if (indexes.putIfAbsent(name, i) != null) {
                throw badRequest("El CSV contiene encabezados duplicados");
            }
        }

        for (String expected : EXPECTED_HEADERS) {
            if (!indexes.containsKey(expected)) {
                throw badRequest(
                        "Falta una columna obligatoria: " + expected
                );
            }
        }

        return indexes;
    }

    private Book validateRow(
            Map<String, String> row,
            int rowNumber,
            Library library,
            List<String> errors
    ) {
        String title = row.get("titulo");
        String author = row.get("autor");
        String isbn = row.get("isbn");
        String description = row.get("descripcion");

        boolean valid = true;

        if (title.isBlank() || title.length() > 255) {
            errors.add("Fila " + rowNumber
                    + ": título obligatorio, máximo 255 caracteres");
            valid = false;
        }

        if (author.isBlank() || author.length() > 150) {
            errors.add("Fila " + rowNumber
                    + ": autor obligatorio, máximo 150 caracteres");
            valid = false;
        }

        if (!IsbnValidator.isValid(isbn)) {
            errors.add("Fila " + rowNumber + ": ISBN inválido");
            valid = false;
        }

        if (description.isBlank() || description.length() > 1000) {
            errors.add("Fila " + rowNumber
                    + ": descripción obligatoria, máximo 1000 caracteres");
            valid = false;
        }

        int pages = 0;
        int year = 0;

        try {
            pages = Integer.parseInt(row.get("cantidad_paginas"));

            if (pages < 1) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ignored) {
            errors.add("Fila " + rowNumber
                    + ": cantidad_paginas debe ser un entero positivo");
            valid = false;
        }

        try {
            year = Integer.parseInt(row.get("anio"));

            if (year < 1 || year > LocalDate.now(ZoneId.systemDefault()).getYear()) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ignored) {
            errors.add("Fila " + rowNumber
                    + ": anio debe ser un año válido no futuro");
            valid = false;
        }

        if (!valid) {
            return null;
        }

        return new Book(
                library,
                isbn,
                title,
                author,
                LocalDate.of(year, 1, 1),
                pages,
                description,
                null
        );
    }

    private static String neutralize(String value) {
        if (!value.isEmpty()
                && "=+-@".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }

        return value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}