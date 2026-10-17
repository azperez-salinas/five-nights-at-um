package librosbuysan.admin.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import librosbuysan.admin.dto.AdminDtos.CreateOwnerRequest;
import librosbuysan.admin.dto.AdminDtos.OwnerCreatedResponse;
import librosbuysan.security.AuthenticatedUser;
import librosbuysan.user.service.UserService;
import librosbuysan.user.dto.UserDtos.UpdateProfileRequest;
import librosbuysan.admin.service.AdminService;

/**
 * R18/F18: las dos unicas acciones del panel de administrador. La
 * restriccion a rol ADMIN vive en SecurityConfig (no depende de que el
 * frontend redirija o no al panel).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;

    public AdminController(AdminService adminService, UserService userService) {
        this.adminService = adminService;
        this.userService = userService;
    }

    @PostMapping("/owners")
    public ResponseEntity<OwnerCreatedResponse> createOwner(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @RequestBody CreateOwnerRequest request) {
        // La respuesta lleva la contrasena temporal: no debe quedar en caches
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(adminService.createOwner(principal.id(), request));
    }

    // Exige la contrasena actual (la validacion es la misma que la del perfil)
    @PutMapping("/password")
    public ResponseEntity<Void> changeOwnPassword(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(principal, request);
        return ResponseEntity.noContent().build();
    }
}
