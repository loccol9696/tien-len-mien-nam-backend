package com.example.be.service;

import com.example.be.entity.User;
import com.example.be.exception.BusinessException;
import com.example.be.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomUserDetailsService implements UserDetailsService {

    UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new BusinessException("Email không tồn tại trong hệ thống", 404)
        );

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .roles(user.getRole().name())
                .disabled(!user.getActive())
                .build();
    }
}
