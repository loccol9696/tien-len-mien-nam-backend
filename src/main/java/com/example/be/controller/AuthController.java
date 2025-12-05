package com.example.be.controller;

import com.example.be.dto.request.*;
import com.example.be.dto.response.ApiResponse;
import com.example.be.dto.response.AuthResponse;
import com.example.be.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Authentication",
        description = "API xử lý đăng ký, đăng nhập và các chức năng xác thực người dùng"
)

public class AuthController {
    AuthService authService;
    StringRedisTemplate template;

    @Operation(
            summary = "Đăng ký tài khoản mới bằng email và password"
    )
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        authService.register(request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Mã OTP đã được gửi đến email. Vui lòng kiểm tra.")
                        .build()
        );
    }


    @Operation(
            summary = "Xác thực đăng ký bằng OTP"
    )
    @PostMapping("/register/verify")
    public ResponseEntity<ApiResponse<Void>> verifyOtpAndRegister(
            @Valid @RequestBody VerifyRegisterRequest request
    ) {
        authService.verifyRegister(request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Xác minh OTP thành công. Tài khoản của bạn đã được tạo.")
                        .build()
        );
    }

    @Operation(
            summary = "Đăng nhập bằng email và password"
    )
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(
                ApiResponse.<AuthResponse>builder()
                        .success(true)
                        .message("Đăng nhập thành công")
                        .data(response)
                        .build()
        );
    }

    @Operation(
            summary = "Quên mật khẩu"
    )
    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Mã OTP đã được gửi đến email. Vui lòng kiểm tra.")
                        .build()
        );
    }


    @Operation(
            summary = "Xác thực quên mật khẩu bằng OTP"
    )
    @PatchMapping("/password/forgot/verify")
    public ResponseEntity<ApiResponse<Void>> verifyForgotPassword(
            @Valid @RequestBody VerifyForgotPasswordRequest request
    ) {
        authService.verifyForgotPassword(request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Mật khẩu đã được đặt lại thành công.")
                        .build()
        );
    }

    @Operation(
            summary = "Đăng nhập bằng tài khoản Google"
    )
    @PostMapping("/login/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request
    ) {
        AuthResponse response = authService.googleLogin(request);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Đăng nhập thành công")
                .data(response)
                .build());
    }


}
