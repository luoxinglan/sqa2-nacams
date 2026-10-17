package cn.sqa2.nacams.auth;

import cn.sqa2.nacams.common.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class AuthService {
    private final Db db;
    private final AccountMapper accounts;
    private final long hours;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public AuthService(Db db, AccountMapper accounts, @Value("${app.token-hours}") long hours) {
        this.db = db; this.accounts = accounts; this.hours = hours;
    }
    public String passwordHash(String value) {
        if (value == null || value.length() < 8 || value.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(422, "密码需至少8个字符且不超过72字节", Map.of("fields", Map.of("password", "长度不合规")));
        return encoder.encode(value);
    }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Transactional
    public Map<String,Object> login(String username, String password) {
        Account account = accounts.selectOne(new QueryWrapper<Account>().eq("username", username));
        if (account == null || !Boolean.TRUE.equals(account.active) || !encoder.matches(password, account.passwordHash))
            throw new ApiException(401, "用户名或密码错误");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(token);
        db.jdbc.update("INSERT INTO auth_sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",
                hash, account.id, Timestamp.from(Instant.now().plus(Duration.ofHours(hours))));
        db.jdbc.update("INSERT INTO signin_logs(user_id,token_hash,signin_at) VALUES(?,?,?)", account.id, hash, Timestamp.from(Instant.now()));
        return Map.of("token", token, "expiresIn", hours * 3600, "user", summary(account));
    }
    public CurrentUser authenticate(String token) {
        var list = db.jdbc.queryForList("SELECT u.*,s.token_hash FROM users u JOIN auth_sessions s ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>? AND u.active=TRUE",
                hash(token), Timestamp.from(Instant.now()));
        if (list.isEmpty()) throw new ApiException(401, "登录已失效，请重新登录");
        var row = list.get(0);
        return new CurrentUser(Db.id(row.get("id")), Db.str(row,"username"), Db.str(row,"role"),
                Db.str(row,"identity_type"), Db.str(row,"name"), Db.bool(row.get("is_director")),
                Db.bool(row.get("is_librarian")), Db.str(row,"token_hash"));
    }
    @Transactional
    public void logout(CurrentUser user) {
        db.jdbc.update("UPDATE signin_logs SET signout_at=? WHERE token_hash=? AND signout_at IS NULL", Timestamp.from(Instant.now()), user.tokenHash());
        db.jdbc.update("DELETE FROM auth_sessions WHERE token_hash=?", user.tokenHash());
    }
    @Transactional
    public void invalidate(long userId) {
        db.jdbc.update("UPDATE signin_logs SET signout_at=? WHERE user_id=? AND signout_at IS NULL", Timestamp.from(Instant.now()), userId);
        db.jdbc.update("DELETE FROM auth_sessions WHERE user_id=?", userId);
    }
    @Transactional
    public void changePassword(CurrentUser user, String oldPassword, String newPassword) {
        var account = accounts.selectById(user.id());
        if (!encoder.matches(oldPassword, account.passwordHash)) throw new ApiException(422, "原密码错误");
        db.jdbc.update("UPDATE users SET password_hash=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", passwordHash(newPassword), user.id());
        invalidate(user.id());
    }
    private Map<String,Object> summary(Account a) {
        return Map.of("id",a.id,"username",a.username,"role",a.role,"identityType",a.identityType,"name",a.name,
                "isDirector",Boolean.TRUE.equals(a.isDirector),"isLibrarian",Boolean.TRUE.equals(a.isLibrarian));
    }
}
