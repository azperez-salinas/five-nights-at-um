package librosbuysan.book.dto;

import java.util.List;

public record CsvImportResult(
        boolean success,
        int imported,
        List<String> errors
) {
    public CsvImportResult {
        errors = List.copyOf(errors);
    }
}