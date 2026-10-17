package cn.sqa2.nacams.auth;

import cn.sqa2.nacams.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class Bootstrap implements ApplicationRunner {
    private final Db db; private final AuthService auth; private final String username,password;
    public Bootstrap(Db db, AuthService auth, @Value("${app.bootstrap.username}") String username,
                     @Value("${app.bootstrap.password}") String password) {
        this.db=db;this.auth=auth;this.username=username;this.password=password;
    }
    @Override public void run(ApplicationArguments args) {
        if (db.jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role='admin' AND active=TRUE",Long.class)>0) return;
        if (password.isBlank()) throw new IllegalStateException("首次运行必须配置BOOTSTRAP_PASSWORD（至少8字符），不会创建默认弱口令账号");
        db.jdbc.update("INSERT INTO users(username,password_hash,role,identity_type,name,profile_json) VALUES(?,?,'admin','staff','系统管理员','{}')",username,auth.passwordHash(password));
    }
}
