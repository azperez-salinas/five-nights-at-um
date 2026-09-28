package librosbuysan.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import librosbuysan.security.AuthenticatedUser;
import librosbuysan.user.UserDtos.ProfileResponse;
import librosbuysan.user.UserDtos.UpdateProfileRequest;
import librosbuysan.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getMe(@AuthenticationPrincipal AuthenticatedUser principal) {
        return withNoStore(HttpStatus.OK, userService.getProfile(principal.id()));
    }

    @PutMapping("/me")
    public ResponseEntity<ProfileResponse> updateMe(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody UpdateProfileRequest request) {
        return withNoStore(HttpStatus.OK, userService.updateProfile(principal.id(), request));
    }

    private static ResponseEntity<ProfileResponse> withNoStore(HttpStatus status, ProfileResponse body) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }
}