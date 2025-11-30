package com.example.be.enums;

import lombok.Getter;

@Getter
public enum OtpType {
    REGISTER("Đăng ký tài khoản"),
    FORGOT_PASSWORD("Quên mật khẩu");

    private final String displayName;

    OtpType(String displayName) {
        this.displayName = displayName;
    }
}
