package com.speednet.module.member.service.auth;

import com.speednet.framework.tenant.core.context.TenantContextHolder;
import com.speednet.module.member.service.user.MemberUserService;
import com.speednet.module.system.api.mail.MailSendApi;
import com.speednet.module.system.api.mail.dto.MailSendSingleToUserReqDTO;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.speednet.framework.common.util.servlet.ServletUtils.getClientIP;
import static com.speednet.framework.web.core.util.WebFrameworkUtils.getTerminal;
import static com.speednet.module.member.enums.ErrorCodeConstants.*;

@Service
public class MemberEmailRegisterService {

    private static final String TEMPLATE_CODE = "member-email-register";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private MemberUserService userService;
    @Resource
    private MailSendApi mailSendApi;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private Environment environment;

    public String sendCode(String rawEmail) {
        String email = normalize(rawEmail);
        if (userService.getUserByEmail(email) != null) {
            throw exception(USER_EMAIL_USED, email);
        }
        String key = key(email);
        Boolean reserved = redisTemplate.opsForValue().setIfAbsent(key + ":cooldown", "1", Duration.ofSeconds(60));
        if (!Boolean.TRUE.equals(reserved)) {
            throw exception(USER_EMAIL_CODE_TOO_FREQUENT);
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        try {
            boolean localTest = environment.acceptsProfiles(Profiles.of("local"));
            if (!localTest) {
                MailSendSingleToUserReqDTO request = new MailSendSingleToUserReqDTO();
                request.setToMails(List.of(email));
                request.setTemplateCode(TEMPLATE_CODE);
                request.setTemplateParams(Map.of("code", code));
                mailSendApi.sendSingleMailToMember(request);
            }
            redisTemplate.opsForValue().set(key, code, Duration.ofMinutes(10));
            redisTemplate.delete(key + ":attempts");
            return localTest ? code : null;
        } catch (RuntimeException error) {
            redisTemplate.delete(key + ":cooldown");
            throw error;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Long register(String rawEmail, String code) {
        String email = normalize(rawEmail);
        String key = key(email);
        Long attempts = redisTemplate.opsForValue().increment(key + ":attempts");
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key + ":attempts", Duration.ofMinutes(10));
        }
        if (attempts != null && attempts > 5) {
            redisTemplate.delete(key);
            throw exception(USER_EMAIL_CODE_TOO_MANY_ATTEMPTS);
        }
        Object expected = redisTemplate.opsForValue().get(key);
        if (expected == null || !expected.equals(code)) {
            throw exception(USER_EMAIL_CODE_INVALID);
        }
        if (userService.getUserByEmail(email) != null) {
            throw exception(USER_EMAIL_USED, email);
        }
        Long userId = userService.createUserByEmail(email, getClientIP(), getTerminal()).getId();
        redisTemplate.delete(List.of(key, key + ":attempts", key + ":cooldown"));
        return userId;
    }

    private String key(String email) {
        return "member:email-register:" + TenantContextHolder.getRequiredTenantId() + ":" + email;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
