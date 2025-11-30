package com.example.be.service;

import com.example.be.dto.request.RegisterRequest;
import com.example.be.enums.OtpType;
import com.example.be.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OtpService {

    RedisTemplate<String, String> redisTemplate;
    ObjectMapper objectMapper;

    static int OTP_EXPIRE_MINUTES = 15;
    static int MAX_OTP_ATTEMPTS = 5;
    static int LOCK_DURATION_MINUTES = 30;
    static int COOLDOWN_MINUTES = 1;

    /**
     * Tạo và lưu OTP vào Redis cho email và loại OTP tương ứng.
     */
    public String generateAndStoreOtp(String email, OtpType type) {
        String lockKey = getLockKey(email, type);

        // Kiểm tra nếu email đang bị khóa tạm thời
        String lockedUntilStr = redisTemplate.opsForValue().get(lockKey);
        if (lockedUntilStr != null && LocalDateTime.parse(lockedUntilStr).isAfter(LocalDateTime.now())) {
            throw new BusinessException("Email đã bị khóa tạm thời do nhập sai OTP quá nhiều lần. Vui lòng thử lại sau.", 403);
        }

        // Kiểm tra thời gian chờ giữa các lần yêu cầu OTP
        String otpRequestKey = getOtpRequestKey(email, type);
        String lastRequestTimeStr = redisTemplate.opsForValue().get(otpRequestKey);
        if (lastRequestTimeStr != null && LocalDateTime.parse(lastRequestTimeStr)
                .plusMinutes(COOLDOWN_MINUTES).isAfter(LocalDateTime.now())) {
            throw new BusinessException("Bạn chỉ có thể yêu cầu mã OTP sau mỗi " + COOLDOWN_MINUTES + " phút. Vui lòng thử lại sau.", 400);
        }

        // Sinh ngẫu nhiên mã OTP gồm 6 chữ số
        String otp = String.valueOf(new Random().nextInt(900000) + 100000);

        // Xóa OTP và số lần thử cũ (nếu có)
        redisTemplate.delete(getOtpKey(email, type));
        redisTemplate.delete(getAttemptKey(email, type));

        // Lưu OTP mới vào Redis và thiết lập thời gian hết hạn
        redisTemplate.opsForValue().set(getOtpKey(email, type), otp, OTP_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // Khởi tạo số lần nhập sai OTP = 0
        redisTemplate.opsForValue().set(getAttemptKey(email, type), "0", OTP_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // Lưu thời điểm yêu cầu OTP để kiểm soát cooldown
        redisTemplate.opsForValue().set(otpRequestKey, LocalDateTime.now().toString(), 1, TimeUnit.MINUTES);

        return otp;
    }

    /**
     * Xác minh mã OTP người dùng nhập vào.
     */
    public void verifyOtp(String email, String otpInput, OtpType type) {
        String otpKey = getOtpKey(email, type);
        String attemptKey = getAttemptKey(email, type);
        String lockKey = getLockKey(email, type);

        // Kiểm tra nếu tài khoản đang bị khóa tạm thời
        String lockedUntilStr = redisTemplate.opsForValue().get(lockKey);
        if (lockedUntilStr != null) {
            LocalDateTime lockedUntil = LocalDateTime.parse(lockedUntilStr);
            if (lockedUntil.isAfter(LocalDateTime.now())) {
                throw new BusinessException("Bạn đã nhập sai OTP quá nhiều lần. Email đang bị khóa tạm thời.", 403);
            } else {
                redisTemplate.delete(lockKey);
            }
        }

        // Kiểm tra OTP có khớp không
        String cachedOtp = redisTemplate.opsForValue().get(otpKey);
        if (cachedOtp != null && cachedOtp.equals(otpInput)) {
            // Xác thực thành công → xóa OTP và dữ liệu liên quan
            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptKey);
            redisTemplate.delete(lockKey);
            return;
        }

        // Nếu sai, tăng số lần thử
        String attemptVal = redisTemplate.opsForValue().get(attemptKey);
        int attempts = attemptVal == null ? 1 : Integer.parseInt(attemptVal) + 1;

        // Nếu vượt quá số lần cho phép → khóa tạm thời
        if (attempts >= MAX_OTP_ATTEMPTS) {
            LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
            redisTemplate.opsForValue().set(lockKey, lockUntil.toString(), LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
            redisTemplate.delete(attemptKey);
            throw new BusinessException("Bạn đã nhập sai OTP quá nhiều lần. Tài khoản tạm thời bị khóa.", 403);
        }

        // Cập nhật lại số lần nhập sai
        redisTemplate.opsForValue().set(attemptKey, String.valueOf(attempts), OTP_EXPIRE_MINUTES, TimeUnit.MINUTES);
        throw new BusinessException("Mã OTP không hợp lệ hoặc đã hết hạn.", 400);
    }

    /**
     * Lưu thông tin đăng ký tạm thời vào Redis (chờ xác minh OTP).
     */
    public void storePendingRegistration(String email, RegisterRequest request) {
        try {
            String key = getRegistrationKey(email);

            // Lưu thông tin đăng ký tạm thời vào Redis (Ghi đè nếu đã tồn tại)
            String json = objectMapper.writeValueAsString(request);

            // Luôn cập nhật thông tin mới nhất
            redisTemplate.opsForValue().set(
                    key,
                    json,
                    OTP_EXPIRE_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            throw new BusinessException("Không thể lưu thông tin đăng ký.", 500);
        }
    }

    /**
     * Lấy thông tin đăng ký tạm thời từ Redis.
     */
    public RegisterRequest getPendingRegistration(String email) {
        try {
            String json = redisTemplate.opsForValue().get(getRegistrationKey(email));
            return json != null ? objectMapper.readValue(json, RegisterRequest.class) : null;
        } catch (JsonProcessingException e) {
            throw new BusinessException("Không thể đọc dữ liệu đăng ký tạm thời.", 500);
        }
    }

    /**
     * Xóa dữ liệu đăng ký tạm thời sau khi xác minh thành công.
     */
    public void deletePendingRegistration(String email) {
        redisTemplate.delete(getRegistrationKey(email));
    }

    private String getOtpKey(String email, OtpType type) {
        return "OTP:" + type.name() + ":" + email;
    }

    private String getAttemptKey(String email, OtpType type) {
        return "OTP_ATTEMPT:" + type.name() + ":" + email;
    }

    private String getLockKey(String email, OtpType type) {
        return "OTP_LOCK:" + type.name() + ":" + email;
    }

    private String getOtpRequestKey(String email, OtpType type) {
        return "OTP_REQUEST:" + type.name() + ":" + email;
    }

    private String getRegistrationKey(String email) {
        return "PENDING_REGISTRATION:" + email;
    }
}
