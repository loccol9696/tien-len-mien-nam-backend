package com.example.be.entity;

import com.example.be.enums.AuthProvider;
import com.example.be.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "users")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(unique = true, nullable = false)
    String email;

    String password;

    @Column(columnDefinition = "NVARCHAR(255)")
    String fullName;

    @Column(columnDefinition = "NVARCHAR(500)")
    String address;

    String phone;

    String avatar;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    AuthProvider authProvider;

    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    LocalDateTime updatedAt = LocalDateTime.now();

    @Builder.Default
    Boolean active = true;
}
