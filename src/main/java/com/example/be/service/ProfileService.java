package com.example.be.service;

import com.example.be.dto.request.UpdateProfileRequest;
import com.example.be.dto.response.ProfileResponse;
import com.example.be.entity.User;
import com.example.be.mapper.UserMapper;
import com.example.be.repository.UserRepository;
import com.example.be.utils.ValidatorUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileService {

    UserMapper userMapper;
    UserRepository userRepository;
    AuthService authService;
    PasswordEncoder passwordEncoder;

    public ProfileResponse getProfile(Authentication authentication) {
        User user = authService.validateUser(authentication);
        return userMapper.toProfileResponse(user);
    }

    public void updateProfile(Authentication authentication, UpdateProfileRequest request) {
        User user = authService.validateUser(authentication);
        if(ValidatorUtils.isValidString(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if(ValidatorUtils.isValidString(request.getFullName())) {
            user.setFullName(request.getFullName());
        }
        if(ValidatorUtils.isValidString(request.getAvatar())) {
            user.setAvatar(request.getAvatar());
        }
        userRepository.save(user);
    }
}
