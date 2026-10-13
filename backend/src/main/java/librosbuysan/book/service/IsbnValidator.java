package librosbuysan.book.service;

import java.util.regex.Pattern;

/**
 * Validacion de ISBN-10 / ISBN-13 con digito verificador. Compartida por la
 * importacion CSV (R17) y la edicion de libros (R18).
 */
final class IsbnValidator {

    private static final Pattern ISBN_10 =
            Pattern.compile("[0-9]{9}[0-9Xx]");

    private static final Pattern ISBN_13 =
            Pattern.compile("[0-9]{13}");

    private IsbnValidator() {
    }

    static boolean isValid(String value) {
        String isbn = value.replaceAll("[- ]", "");

        if (ISBN_10.matcher(isbn).matches()) {
            int sum = 0;

            for (int i = 0; i < 10; i++) {
                int digit = i == 9
                        && (isbn.charAt(i) == 'X'
                        || isbn.charAt(i) == 'x')
                        ? 10
                        : isbn.charAt(i) - '0';

                sum += digit * (10 - i);
            }

            return sum % 11 == 0;
        }

        if (ISBN_13.matcher(isbn).matches()) {
            int sum = 0;

            for (int i = 0; i < 13; i++) {
                int digit = isbn.charAt(i) - '0';
                sum += digit * (i % 2 == 0 ? 1 : 3);
            }

            return sum % 10 == 0;
        }

        return false;
    }
}
