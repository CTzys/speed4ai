# Clash 订阅路由规则

后台入口：订阅管理 → Clash 规则。权限：`subscription:clash-rule:manage`，普通管理员需在角色管理中分配该菜单权限。
部署主后台与管理前端，并执行 Flyway `V10__customer_support_and_clash_rules.sql` 后启用。

## 生效逻辑

数据表 `subscription_clash_rule`，唯一键 `(tenant_id, user_id)`。`user_id=0` 表示租户通用规则；其他值为客户 ID。
每次生成 Clash 配置读取数据库，不缓存规则。

1. 有用户专用规则：完整使用专用规则，不与通用规则合并。
2. 无用户专用规则：使用当前租户的通用规则。
3. 无通用记录：使用原有系统默认规则；迁移会为现有租户保存默认规则。

后台选择「通用规则」编辑全体客户默认规则；选择「指定用户」搜索昵称、邮箱或客户 ID。
未配置专用规则的用户会展示继承的通用规则，保存后成为独立的专用规则。
删除专用规则后恢复继承。规则变更不改变节点分配及订阅有效期。
客户需要重新拉取或更新订阅才能使用新规则，后续编辑不需重新部署。

## 格式

每行一条，按从上往下的顺序匹配。例如：

```text
DOMAIN-SUFFIX,example.com,DIRECT
DOMAIN-KEYWORD,advertisement,REJECT
IP-CIDR,192.168.0.0/16,DIRECT,no-resolve
MATCH,SpeedNet
```

策略支持 `DIRECT`、`REJECT`、`SpeedNet`；SpeedNet 是订阅内的节点选择代理组。
类型支持 `DOMAIN`、`DOMAIN-SUFFIX`、`DOMAIN-KEYWORD`、`IP-CIDR`、`IP-CIDR6`、`SRC-IP-CIDR`、`GEOIP`、`DST-PORT`、`SRC-PORT`、`NETWORK`、`MATCH`。
端口规则使用单端口，NETWORK 使用 TCP 或 UDP。需要外部规则集的 RULE-SET 和逻辑组合规则当前不支持。
GEOIP 需要客户端提供相应 GeoIP 数据。
最多 1000 条，每条最多 1024 字符。MATCH 只能位于最后；未填写会自动追加 MATCH,SpeedNet。

## 管理接口

均使用后台管理员认证和上述权限，不开放给客户自行修改。

| 方法 | 路径 | 参数 |
| --- | --- | --- |
| GET | `/admin-api/subscription/clash-rule/get` | userId，0 为通用；返回 userId、custom、rules |
| POST | `/admin-api/subscription/clash-rule/save` | JSON `{ "userId": 0, "rules": ["MATCH,SpeedNet"] }` |
| DELETE | `/admin-api/subscription/clash-rule/delete` | userId；删除用户记录恢复继承，删除通用记录恢复系统默认 |
| GET | `/admin-api/subscription/clash-rule/users` | keyword，当前租户最多 50 个匹配客户 |
| GET | `/admin-api/subscription/clash-rule/list` | 当前租户配置了专用规则的客户列表 |

原有 Base64 订阅输出不受影响。Clash 下载接口与 `?format=clash` 订阅链接均应用上述规则。
