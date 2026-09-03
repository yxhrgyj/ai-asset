package com.company.aiasset.bootstrap;

import com.company.aiasset.user.User;
import com.company.aiasset.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次启动时创建初始管理员账号。
 *
 * 用 ApplicationRunner 而非 SQL 脚本写死哈希值的原因：
 * 1. SQL 里的 bcrypt 哈希无法验证是否对应明文口令；
 * 2. 避免在版本控制里留下可被离线破解的哈希；
 * 3. PasswordEncoder 保证算法参数与 SecurityConfig 一致。
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${app.bootstrap.admin-username}")
    private String adminUsername;

    @Value("${app.bootstrap.admin-password}")
    private String adminPassword;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.findByUsernameIgnoreCase(adminUsername).isPresent()) {
            log.info("初始管理员账号已存在,跳过创建");
            return;
        }

        User admin = new User(adminUsername, "系统管理员",
                encoder.encode(adminPassword), User.Role.ADMIN);
        admin.setStatus(User.Status.ACTIVE);
        admin.setMustChangePassword(true);

        users.save(admin);
        log.warn("已创建初始管理员账号: {} (请立即登录并修改口令)", adminUsername);
    }
}
