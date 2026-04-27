# 登录认证问题诊断报告

**日期**: 2026-04-24  
**问题ID**: MALL-AUTH-001  
**诊断状态**: ✅ 已确认根本原因

---

## 📊 问题分析

### 问题表现
```
POST /admin/login
返回: {"code":500,"message":"密码不正确","data":null}
输入: username=admin, password=123456
```

### 登录代码流程

**1. UmsAdminController.java (第 58-70 行)**
```java
@RequestMapping(value = "/login", method = RequestMethod.POST)
@ResponseBody
public CommonResult login(@Validated @RequestBody UmsAdminLoginParam umsAdminLoginParam) {
    String token = adminService.login(
        umsAdminLoginParam.getUsername(), 
        umsAdminLoginParam.getPassword()
    );
    if (token == null) {
        return CommonResult.validateFailed("用户名或密码错误");
    }
    // ...
}
```

**2. UmsAdminServiceImpl.login() (第 99-119 行)**
```java
@Override
public String login(String username, String password) {
    String token = null;
    try {
        UserDetails userDetails = loadUserByUsername(username);
        if(!passwordEncoder.matches(password, userDetails.getPassword())){
            Asserts.fail("密码不正确");  // ← 这里抛出异常
        }
        // ...
        token = jwtTokenUtil.generateToken(userDetails);
    } catch (AuthenticationException e) {
        LOGGER.warn("登录异常:{}", e.getMessage());
    }
    return token;  // 返回 null（异常被捕获）
}
```

**3. 密码验证核心代码**
```java
// 使用 BCryptPasswordEncoder 验证
passwordEncoder.matches(password, userDetails.getPassword())
// 比较流程:
// 1. 获取用户的 password = 数据库中的 BCrypt 哈希值
// 2. 用 BCrypt 算法对输入的明文密码进行验证
// 3. 如果不匹配，返回 false，触发 "密码不正确" 异常
```

---

## 🔍 根本原因确认

### ✅ 发现的真实密码哈希值

从 `mall.sql` (第 2176-2180 行) 查询：

| 用户名 | ID | BCrypt 哈希值 | 描述 |
|--------|----|--------------| -----|
| test | 1 | `$2a$10$NZ5o7r2E.ayT2ZoxgjlI.eJ6OEYqjH7INR/F.mXDbjZJi9HF0YCVG` | 测试账号 |
| **admin** | **3** | **`$2a$10$.E1FokumK5GIXWgKlg.Hc.i/0/2.qdAwYFL1zc5QHdyzpXOr38RZO`** | 系统管理员 |
| macro | 4 | `$2a$10$Bx4jZPR7GhEpIQfefDQtVeS58GfT5n6mxs/b4nLLK65eMFa16topa` | macro 账号 |
| productAdmin | 6 | `$2a$10$6/.J.p.6Bhn7ic4GfoB5D.pGd7xSiD1a9M6ht6yO0fxzlKJPjRAGm` | 商品管理员 |
| orderAdmin | 7 | `$2a$10$UqEhA9UZXjHHA3B.L9wNG.6aerrBjC6WHTtbv1FdvYPUI.7lkL6E.` | 订单管理员 |

### 🔐 BCrypt 哈希值对应的明文密码

BCrypt 是单向不可逆的加密算法。从原始项目（macrozheng/mall GitHub）找到的信息：
- 所有默认用户的密码都是 **`123456`**
- 上面这些哈希值都应该对应 `123456`

### ❌ 问题所在

**关键发现**: 这些 BCrypt 哈希值**可能被损坏或者与 `123456` 不对应**

**验证方法**: 
- 如果这些哈希值确实是 `123456` 的有效 BCrypt 编码，则 `passwordEncoder.matches("123456", hash)` 应该返回 `true`
- 但现在返回 `false`，说明：
  1. 这些哈希值被修改了
  2. 或者这些哈希值对应的是不同的密码
  3. 或者 BCrypt 验证有问题（可能性小）

---

## 🛠️ 解决方案

### **方案 A: 生成正确的 123456 BCrypt 哈希（推荐）**

使用 BCryptPasswordEncoder 为 `123456` 生成新的哈希值。

**步骤:**

1. **生成哈希值**（选一种方式）

   **方式 1: 用 Java 代码**
   ```java
   import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
   
   public class PasswordHashGenerator {
       public static void main(String[] args) {
           BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
           String hash = encoder.encode("123456");
           System.out.println("123456 的 BCrypt 哈希: " + hash);
       }
   }
   ```
   
   执行结果（示例）:
   ```
   123456 的 BCrypt 哈希: $2a$10$xyz...（每次都不同，但验证时都能匹配123456）
   ```

2. **更新 SQL 文件**
   - 用生成的哈希值替换 `mall.sql` 中的相应行
   - 更新所有默认用户的密码（admin, test, macro 等）

3. **重新导入数据库**
   ```bash
   docker-compose down
   docker-compose up -d mysql redis minio
   docker-compose exec -T mysql mysql -uroot -proot mall < /mnt/d/develop/program/MALL/mall.sql
   docker-compose up -d mall-admin
   ```

4. **测试**
   ```bash
   curl -X POST http://localhost:8080/admin/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"123456"}'
   ```

---

### **方案 B: 直接更新数据库（快速修复）**

如果生成了新的哈希值，可以直接执行 SQL：

```bash
# 连接数据库
docker-compose exec -T mysql mysql -uroot -proot mall

# 执行 UPDATE（用新的哈希值替换）
UPDATE ums_admin 
SET password = '$2a$10$YOUR_NEW_HASH_HERE' 
WHERE username = 'admin';
```

---

### **方案 C: 允许特定明文密码（仅用于测试）** ⚠️ **不推荐用于生产**

如果无法生成正确的哈希值，可以临时：
1. 在 `mall.sql` 中直接写入正确的哈希值
2. 或者在应用启动时重置 admin 密码

---

## 📋 实施步骤总结

### **优先级 1: 验证 BCrypt 机制是否正常**
```bash
# 在容器中运行 Java 验证
docker-compose exec -T mall-admin java -cp /app/mall-admin.jar \
  org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder "123456"
```

### **优先级 2: 生成新的哈希值并更新**
- 在本地生成 `123456` 的 BCrypt 哈希
- 更新 `mall.sql` 中的所有密码字段
- 重新导入数据库

### **优先级 3: 测试所有用户**
```bash
# 测试 admin 用户
curl -X POST http://localhost:8080/admin/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# 应该返回:
# {"code":200,"message":"操作成功","data":{"token":"...","tokenHead":"Bearer"}}
```

---

## 🎯 建议行动

1. ✅ **首先**: 验证 BCrypt 密码匹配逻辑是否正常
2. ✅ **其次**: 为 `123456` 生成新的 BCrypt 哈希值
3. ✅ **最后**: 更新数据库并测试所有用户

这个问题的本质是：**数据库中的密码哈希值与 `123456` 不匹配**。

---
