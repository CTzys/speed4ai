-- Email verification uses the existing mail account and template infrastructure.
-- Configure the selected SMTP account before accepting external registrations.
INSERT INTO system_mail_template
    (name, code, account_id, nickname, title, content, params, status, remark, creator, updater, deleted)
SELECT '会员邮箱注册验证码', 'member-email-register', account.id, '会员中心',
       '会员注册验证码', '<p>您的会员注册验证码是 {code}，10 分钟内有效。若非本人操作，请忽略此邮件。</p>',
       '["code"]', 0, '会员邮箱注册', '1', '1', b'0'
FROM system_mail_account account
WHERE account.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_mail_template WHERE code = 'member-email-register' AND deleted = b'0')
ORDER BY account.id
LIMIT 1;

CREATE UNIQUE INDEX uk_member_user_tenant_email ON member_user (tenant_id, email);
