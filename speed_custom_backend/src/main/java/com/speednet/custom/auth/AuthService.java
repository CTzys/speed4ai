package com.speednet.custom.auth;

import com.speednet.custom.auth.AuthDtos.*;
import com.speednet.custom.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final JavaMailSender mailSender;
    private final Environment environment;
    private final TransactionTemplate transactions;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    @Value("${custom.tenant-id}") private long tenantId;
    @Value("${custom.dev-return-code}") private boolean devReturnCode;
    @Value("${custom.mail-from}") private String mailFrom;

    public AuthService(JdbcTemplate jdbc, JavaMailSender mailSender, Environment environment, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc; this.mailSender = mailSender; this.environment = environment;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    public String sendCode(String rawEmail) {
        String email = normalize(rawEmail);
        if (!findMember(email).isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "邮箱已注册，请直接登录");
        LocalDateTime now = LocalDateTime.now();
        List<LocalDateTime> sent = jdbc.query("SELECT last_sent_at FROM custom_email_code WHERE tenant_id=? AND email=?",
                (rs, row) -> rs.getTimestamp(1).toLocalDateTime(), tenantId, email);
        if (!sent.isEmpty() && sent.getFirst().plusSeconds(60).isAfter(now))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "请一分钟后再获取验证码");
        String code = String.format("%06d", random.nextInt(1_000_000));
        String codeHash = passwords.encode(code);
        // The stored hash is deliberately slow to check; a leaked six-digit digest must not be trivial to enumerate.
        jdbc.update("INSERT INTO custom_email_code (tenant_id,email,code_hash,expires_at,last_sent_at,attempts,consumed) " +
                        "VALUES (?,?,?,?,?,0,FALSE) ON DUPLICATE KEY UPDATE code_hash=VALUES(code_hash)," +
                        "expires_at=VALUES(expires_at),last_sent_at=VALUES(last_sent_at),attempts=0,consumed=FALSE",
                tenantId, email, codeHash, now.plusMinutes(10), now);
        if (devReturnCode && environment.matchesProfiles("local", "test")) return code;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("注册验证码");
        message.setText("您的注册验证码是 " + code + "，10 分钟内有效。如非本人操作，请忽略此邮件。");
        try { mailSender.send(message); }
        catch (RuntimeException error) {
            jdbc.update("DELETE FROM custom_email_code WHERE tenant_id=? AND email=? AND code_hash=?", tenantId, email, codeHash);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "邮件发送失败，请稍后重试");
        }
        return null;
    }

    public SessionResponse register(RegisterRequest request, String ip) {
        String email = normalize(request.email());
        List<CodeRow> codes = jdbc.query("SELECT code_hash, expires_at, attempts, consumed FROM custom_email_code WHERE tenant_id=? AND email=?",
                (rs, row) -> new CodeRow(rs.getString(1), rs.getTimestamp(2).toLocalDateTime(), rs.getInt(3), rs.getBoolean(4)), tenantId, email);
        if (codes.isEmpty() || codes.getFirst().consumed() || codes.getFirst().expiresAt().isBefore(LocalDateTime.now()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取");
        CodeRow code = codes.getFirst();
        if (code.attempts() >= 5) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "验证码错误次数过多，请重新获取");
        if (!passwords.matches(request.code(), code.hash())) {
            jdbc.update("UPDATE custom_email_code SET attempts=attempts+1 WHERE tenant_id=? AND email=?", tenantId, email);
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码不正确");
        }
        return transactions.execute(status -> {
            List<CodeRow> locked = jdbc.query("SELECT code_hash, expires_at, attempts, consumed FROM custom_email_code WHERE tenant_id=? AND email=? FOR UPDATE",
                    (rs, row) -> new CodeRow(rs.getString(1), rs.getTimestamp(2).toLocalDateTime(), rs.getInt(3), rs.getBoolean(4)), tenantId, email);
            if (locked.isEmpty() || locked.getFirst().consumed() || locked.getFirst().expiresAt().isBefore(LocalDateTime.now())
                    || !locked.getFirst().hash().equals(code.hash()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取");
            if (!findMember(email).isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "邮箱已注册，请直接登录");
            String nickname = "用户" + String.format("%06d", random.nextInt(1_000_000));
            jdbc.update("INSERT INTO member_user (tenant_id,email,password,status,register_ip,register_terminal,nickname,point,experience,deleted) " +
                    "VALUES (?,?,?,0,?,1,?,0,0,FALSE)", tenantId, email, passwords.encode(request.password()), ip, nickname);
            jdbc.update("UPDATE custom_email_code SET consumed=TRUE WHERE tenant_id=? AND email=?", tenantId, email);
            return createSession(findMember(email).getFirst());
        });
    }

    public SessionResponse login(LoginRequest request, String ip) {
        List<MemberRow> users = findMember(normalize(request.email()));
        if (users.isEmpty() || !passwords.matches(request.password(), users.getFirst().password()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "邮箱或密码不正确");
        MemberRow member = users.getFirst();
        if (member.status() != 0) throw new ApiException(HttpStatus.FORBIDDEN, "账号已被禁用");
        jdbc.update("UPDATE member_user SET login_ip=?,login_date=? WHERE id=? AND tenant_id=?", ip, LocalDateTime.now(), member.id(), tenantId);
        return createSession(member);
    }

    @Transactional
    public SessionResponse refresh(String refreshToken) {
        List<Long> ids = jdbc.query("SELECT member_user_id FROM custom_session WHERE tenant_id=? AND refresh_hash=? AND revoked=FALSE AND refresh_expires_at>? FOR UPDATE",
                (rs, row) -> rs.getLong(1), tenantId, TokenUtils.hash(refreshToken), LocalDateTime.now());
        if (ids.isEmpty()) throw new ApiException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录");
        MemberRow member = findMemberById(ids.getFirst());
        jdbc.update("UPDATE custom_session SET revoked=TRUE WHERE tenant_id=? AND refresh_hash=?", tenantId, TokenUtils.hash(refreshToken));
        return createSession(member);
    }

    public MemberResponse me(long userId) { return response(findMemberById(userId)); }

    public void logout(String accessToken) {
        jdbc.update("UPDATE custom_session SET revoked=TRUE WHERE tenant_id=? AND access_hash=?", tenantId, TokenUtils.hash(accessToken));
    }

    public long authenticate(String accessToken) {
        List<Long> ids = jdbc.query("SELECT member_user_id FROM custom_session WHERE tenant_id=? AND access_hash=? AND revoked=FALSE AND access_expires_at>?",
                (rs, row) -> rs.getLong(1), tenantId, TokenUtils.hash(accessToken), LocalDateTime.now());
        if (ids.isEmpty()) throw new ApiException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录");
        return findMemberById(ids.getFirst()).id();
    }

    private List<MemberRow> findMember(String email) {
        return jdbc.query("SELECT id,email,password,nickname,avatar,status FROM member_user WHERE tenant_id=? AND email=? AND deleted=FALSE",
                (rs, row) -> new MemberRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6)), tenantId, email);
    }

    private MemberRow findMemberById(long id) {
        List<MemberRow> users = jdbc.query("SELECT id,email,password,nickname,avatar,status FROM member_user WHERE tenant_id=? AND id=? AND deleted=FALSE",
                (rs, row) -> new MemberRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6)), tenantId, id);
        if (users.isEmpty()) throw new ApiException(HttpStatus.UNAUTHORIZED, "账号不存在");
        MemberRow member = users.getFirst();
        if (member.status() != 0) throw new ApiException(HttpStatus.FORBIDDEN, "账号已被禁用");
        return member;
    }

    private SessionResponse createSession(MemberRow member) {
        String access = TokenUtils.randomToken(), refresh = TokenUtils.randomToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusHours(2);
        jdbc.update("INSERT INTO custom_session (tenant_id,member_user_id,access_hash,refresh_hash,access_expires_at,refresh_expires_at) VALUES (?,?,?,?,?,?)",
                tenantId, member.id(), TokenUtils.hash(access), TokenUtils.hash(refresh), expires, now.plusDays(14));
        return new SessionResponse(access, refresh, expires.toString(), response(member));
    }

    private MemberResponse response(MemberRow member) { return new MemberResponse(member.id(), member.email(), member.nickname(), member.avatar()); }
    private record CodeRow(String hash, LocalDateTime expiresAt, int attempts, boolean consumed) {}
    private record MemberRow(long id, String email, String password, String nickname, String avatar, int status) {}
}
