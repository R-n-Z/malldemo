package com.macro.mall.portal;
import cn.hutool.crypto.digest.BCrypt;

public class AdminTest {

    public static void main(String[] args) {
        String password = "admin123";
        String encryptPwd = BCrypt.hashpw(password);
        System.out.println("encryptPwd:"+encryptPwd);
    }
}
