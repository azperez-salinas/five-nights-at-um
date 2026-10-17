package librosbuysan.admin.service;

import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import librosbuysan.admin.dto.AdminDtos.CreateOwnerRequest;
import librosbuysan.admin.dto.AdminDtos.OwnerCreatedResponse;
import librosbuysan.auth.service.PasswordHasher;
import librosbuysan.library.entity.Library;
import librosbuysan.library.repository.LibraryRepo;
import librosbuysan.user.entity.User;
import librosbuysan.user.repository.UserRepo;

/**
 * R18/F18: alta de duenos desde el panel de administrador. Reemplaza al
 * registro publico de duenos, que ya no existe.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private static final String INVALID_REQUEST = "Solicitud invalida";
    private static final String ALREADY_REGISTERED = "El usuario o el email ya estan registrados";

    private final UserRepo userRepo;
    private final LibraryRepo libraryRepo;
    private final PasswordHasher passwordHasher;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final Validator validator;

    public AdminService(UserRepo userRepo, LibraryRepo libraryRepo, PasswordHasher passwordHasher,
                        TemporaryPasswordGenerator passwordGenerator, Validator validator) {
        this.userRepo = userRepo;
        this.libraryRepo = libraryRepo;
        this.passwordHasher = passwordHasher;
        this.passwordGenerator = passwordGenerator;
        this.validator = validator;
    }

    /**
     * Crea el dueno y su libreria en una sola transaccion: si falla el insert
     * de la libreria, el del usuario se revierte (no quedan duenos sin
     * libreria). La contrasena temporal se devuelve una unica vez y en la base
     * solo queda su hash.
     */
    @Transactional
    public OwnerCreatedResponse createOwner(Long adminId, CreateOwnerRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            log.warn("Alta de dueno fallida: adminId={} motivo=request_invalido", adminId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
        }
        String username = normalize(request.username());
        String email = normalize(request.email());
        String nombreLibreria = request.nombreLibreria().trim();

        if (userRepo.existsByUsername(username) || userRepo.existsByEmail(email)) {
            log.warn("Alta de dueno fallida: adminId={} username={} motivo=duplicado", adminId, username);
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_REGISTERED);
        }

        char[] passwordTemporal = passwordGenerator.generate();
        try {
            User owner = User.duenoConPasswordTemporal(username, email, passwordHasher.hash(passwordTemporal));
            try {
                owner = userRepo.saveAndFlush(owner);
            } catch (DataIntegrityViolationException e) {
                // Otro alta con el mismo username/email se guardo entre el chequeo y el insert
                log.warn("Alta de dueno fallida: adminId={} username={} motivo=duplicado_concurrente",
                        adminId, username);
                throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_REGISTERED);
            }
            Library library = libraryRepo.saveAndFlush(new Library(owner, nombreLibreria));

            // Nunca se loguea la contrasena temporal
            log.info("Dueno creado por admin: adminId={} ownerId={} username={} libraryId={}",
                    adminId, owner.getId(), username, library.getId());
            return new OwnerCreatedResponse(owner.getId(), owner.getUsername(), owner.getEmail(),
                    library.getId(), library.getNombre(), passwordTemporal);
        } catch (RuntimeException e) {
            // Si no se devuelve, la contrasena no tiene que quedar en memoria
            Arrays.fill(passwordTemporal, '\0');
            throw e;
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
