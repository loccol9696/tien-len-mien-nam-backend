package com.example.be.service;

import com.example.be.dto.request.*;
import com.example.be.dto.response.AuthResponse;
import com.example.be.entity.User;
import com.example.be.enums.AuthProvider;
import com.example.be.enums.OtpType;
import com.example.be.exception.BusinessException;
import com.example.be.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthService {

    final UserRepository userRepository;
    final PasswordEncoder passwordEncoder;
    final OtpService otpService;
    final MailService mailService;
    final JwtService jwtService;
    final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.client-id}")
    String googleClientId;

    @Value("${google.client-secret}")
    String googleClientSecret;

    @Value("${google.redirect-uri}")
    String googleRedirectUri;

    /**
     * Đăng ký tài khoản mới bằng email và password.
     */
    public void register(RegisterRequest request) {
        String email = request.getEmail();

        if(userRepository.existsByEmail(email)) {
            throw new BusinessException("Email đã tồn tại trong hệ thống", 400);
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Mật khẩu xác nhận không khớp", 400);
        }

        // Lưu thông tin đăng ký tạm thời trong Redis
        otpService.storePendingRegistration(email, request);

        // Sinh và gửi OTP
        String otp = otpService.generateAndStoreOtp(email, OtpType.REGISTER);
        mailService.sendOtpEmail(email, otp, OtpType.REGISTER);
    }

    /**
     * Xác thực đăng ký tài khoản bằng OTP
     */
    public void verifyRegister(VerifyRegisterRequest request) {
        String email = request.getEmail();
        String otpInput = request.getOtpCode();

        // Kiểm tra OTP
        otpService.verifyOtp(email, otpInput, OtpType.REGISTER);

        // Lấy dữ liệu đăng ký đã lưu tạm trong Redis
        RegisterRequest pendingRequest = otpService.getPendingRegistration(email);
        if (pendingRequest == null) {
            throw new BusinessException("Dữ liệu đăng ký đã hết hạn hoặc không tìm thấy.", 400);
        }

        // Kiểm tra lại
        if(userRepository.existsByEmail(email)) {
            throw new BusinessException("Email đã tồn tại trong hệ thống", 400);
        }

        User user = User.builder()
                .email(pendingRequest.getEmail())
                .password(passwordEncoder.encode(pendingRequest.getPassword()))
                .fullName(pendingRequest.getFullName())
                .authProvider(AuthProvider.NONE)
                .build();

        userRepository.save(user);

        // Xóa dữ liệu đã lưu trong Redis
        otpService.deletePendingRegistration(email);
    }

    /**
     * Đăng nhập bằng gmail và password
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(
                () -> new BusinessException("Email không tồn tại trong hệ thống", 404)
        );

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("Mật khẩu không chính xác", 400);
        }

        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .build();
    }

    /**
     * Quên mật khẩu
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail();

        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new BusinessException("Email không tồn tại trong hệ thống", 400)
        );

        if(user.getAuthProvider() != AuthProvider.NONE) {
            throw new BusinessException("Tài khoản này không hỗ trợ chức năng quên mật khẩu", 400);
        }

        // Sinh và gửi OTP
        String otp = otpService.generateAndStoreOtp(email, OtpType.FORGOT_PASSWORD);
        mailService.sendOtpEmail(user.getEmail(), otp, OtpType.FORGOT_PASSWORD);
    }

    /**
     * Xác thực quên mật khẩu bằng OTP
     */
    public void verifyForgotPassword(VerifyForgotPasswordRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Mật khẩu xác nhận không khớp", 400);
        }

        String email = request.getEmail();

        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new BusinessException("Email không tồn tại trong hệ thống", 400)
        );

        if(user.getAuthProvider() != AuthProvider.NONE) {
            throw new BusinessException("Tài khoản này không hỗ trợ chức năng quên mật khẩu", 400);
        }

        otpService.verifyOtp(email, request.getOtpCode(), OtpType.FORGOT_PASSWORD);

        user.setPassword(passwordEncoder.encode(request.getPassword()));

        userRepository.save(user);
    }

    /**
     * Đăng nhập với Google bằng authorization code
     */
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        String code = request.getAuthorizationCode();

        try {
            // Gửi code lên Google để lấy access_token + id_token
            String tokenUrl = "https://oauth2.googleapis.com/token";

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", code);
            params.add("client_id", googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("redirect_uri", googleRedirectUri);
            params.add("grant_type", "authorization_code");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> httpRequest = new HttpEntity<>(params, headers);

            Map tokenResponse = restTemplate.postForObject(tokenUrl, httpRequest, Map.class);

            if (tokenResponse == null || !tokenResponse.containsKey("id_token")) {
                throw new RuntimeException("Không nhận được id_token từ Google.");
            }

            String idToken = (String) tokenResponse.get("id_token");

            // Verify id_token
            GoogleIdToken.Payload payload = verifyIdToken(idToken);

            String email = payload.getEmail();
            String fullName = (String) payload.get("name");

            // Tạo user nếu chưa tồn tại
            User user = userRepository.findByEmail(email).orElseGet(() -> {
                User u = User.builder()
                        .email(email)
                        .fullName(fullName)
                        .authProvider(AuthProvider.GOOGLE)
                        .password("")
                        .build();
                return userRepository.save(u);
            });

            // Tạo JWT
            String jwt = jwtService.generateToken(user);
            return AuthResponse.builder().token(jwt).build();

        } catch (Exception e) {
            throw new BusinessException("Đăng nhập Google thất bại: " + e.getMessage(), 500);
        }
    }


    /**
     * Verify Google ID Token với GsonFactory
     */
    private GoogleIdToken.Payload verifyIdToken(String idTokenString) throws Exception {
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                jsonFactory
        ).setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken == null) {
            throw new RuntimeException("id_token không hợp lệ.");
        }
        return idToken.getPayload();
    }
}
