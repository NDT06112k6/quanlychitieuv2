package com.example.expensetracker.user;

import com.example.expensetracker.auth.AuthPrincipal;
import com.example.expensetracker.common.web.ApiDocumentation;
import com.example.expensetracker.common.web.ApiErrorResponses;
import com.example.expensetracker.common.web.ApiRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Users", description = "Profile and password of the authenticated user")
@SecurityRequirement(name = ApiDocumentation.BEARER_SCHEME)
@ApiErrorResponses
public class UserController {

    private final UserService userService;

    @GetMapping(ApiRoutes.USER_ME)
    @Operation(summary = "Get my profile")
    @ApiResponse(responseCode = "200", description = "Current user profile")
    public UserResponse getProfile(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal) {
        UserResponse response = userService.getProfile(principal.userId());
        return response;
    }

    @PutMapping(ApiRoutes.USER_ME)
    @Operation(summary = "Change my display name")
    @ApiResponse(responseCode = "200", description = "Updated profile")
    public UserResponse updateProfile(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UserProfileRequest request) {
        UserResponse response = userService.updateProfile(principal.userId(), request);
        return response;
    }

    @PutMapping(ApiRoutes.USER_PASSWORD)
    @Operation(summary = "Change my password")
    @ApiResponse(responseCode = "200", description = "Password changed")
    public ResponseEntity<Void> changePassword(@Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(principal.userId(), request);
        ResponseEntity<Void> response = ResponseEntity.ok().build();
        return response;
    }
}
