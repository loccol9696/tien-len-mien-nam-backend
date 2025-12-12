package com.example.be.controller;

import com.example.be.dto.request.UpdateProfileRequest;
import com.example.be.dto.response.ApiResponse;
import com.example.be.dto.response.ProfileResponse;
import com.example.be.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/profile")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Profile",
        description = "API xử lý thông tin cá nhân của người dùng"
)
public class ProfileController {

    ProfileService profileService;

    @GetMapping
    @Operation(
            summary = "Lấy thông tin cá nhân"
    )
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(Authentication authentication) {
        ProfileResponse response = profileService.getProfile(authentication);
        ApiResponse<ProfileResponse> apiResponse = ApiResponse.<ProfileResponse>builder()
                .success(true)
                .message("Lấy thông tin cá nhân thành công")
                .data(response)
                .build();
        return ResponseEntity.ok(apiResponse);
    }

    @PatchMapping
    @Operation(
            summary = "Cập nhật thông tin cá nhân"
    )
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            Authentication authentication, @RequestBody UpdateProfileRequest request
    ) {
        profileService.updateProfile(authentication, request);
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(true)
                .message("Cập nhật thông tin cá nhân thành công")
                .build();
        return ResponseEntity.ok(apiResponse);
    }
}
