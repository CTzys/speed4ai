# 客户工单

工单业务由 `speednet-module-support` 统一管理。客户前台通过客户后台的固定代理 `/custom-api/tickets` 访问主后台 `/app-api/support/tickets`；管理前台访问 `/admin-api/support/tickets`。两个后台共享会员和会话数据库。主后台再次验证客户会话，忽略客户端声称的会员和租户身份。

## 启用

1. 构建并部署主后台、客户后台、管理前台和客户前台。
2. 主后台启动时执行新增 Flyway `V10__customer_support_and_clash_rules.sql`。保留原 V1–V9，不修改历史迁移；V9 的历史合并要求仍按原商品文档执行。
3. 客户后台的 `CUSTOM_BACKEND_URL` 指向主后台内部地址，`CUSTOM_TENANT_ID` 与客户会话租户一致。
4. 给客服角色分配 `support:ticket:query`、`support:ticket:reply`、`support:ticket:assign`（领取）及按需分配 `support:ticket:close`。主管另加 `support:ticket:manage`。分配时只提供当前租户内启用且具备查询、回复权限的员工。
5. 管理端重新登录刷新菜单，进入“工单管理”；客户中心进入“工单系统”。订单列表、订单详情、首页订阅卡片可直接发起关联工单。
6. 如果网关设有上传体积限制，至少允许 6 MB 请求；应用仅接受每张不超过 5 MB 的截图。

新增五张表：`support_ticket`、`support_ticket_message`、`support_ticket_attachment`、`support_ticket_event`、`support_ticket_read`。第一版截图保存在专用 `mediumblob` 字段中，通过鉴权接口读取，不复用现有公开文件链接。备份数据库时同时备份截图，后续大规模使用可迁移到私有对象存储。未发送截图 24 小时后失效；每小时批量清理过期未发送截图，已关联对话的截图保留。

## 页面与规则

客户可筛选与搜索自己的工单、提交问题、关联自己的订单或订阅、上传截图、回复、确认解决或撤回关闭、重新打开。打开对话后按服务端返回的最新可见消息位置标记已读，后到回复仍保持未读；导航未读角标每 30 秒在页面可见时更新。

客服可查看未分配及自己负责的工单，主管可查看当前租户全部工单。详情、回复、附件、记录和已读接口都检查范围。领取、转交、优先级、关闭与重新打开记录操作日志；回复和内部备注不允许编辑或删除。消息及操作记录每次最多加载 50 条，可继续加载历史。

分类：连接异常、订阅与流量、订单与支付、账户问题、建议与其他。优先级由员工设置。客户详情不返回内部备注、员工编号、优先级及内部截图；内部备注不改变客户可见的状态、排序时间或未读数。

状态：`pending` 待处理、`processing` 处理中、`waiting` 等待客户、`resolved` 已解决、`closed` 已关闭。新建为待处理；领取待处理工单后进入处理中；客服公开回复时选择处理中、等待客户或已解决；客户回复进入处理中。内部备注保持原状态。关闭必填原因，客户关闭后 7 天内可重开，员工可重开权限范围内的超期工单。已关闭工单先重开再回复。

关联信息保留提交时快照，后台侧栏另查当前业务状态。删除关联订阅后仍可查看工单和提交时快照。工单不直接执行退款、权益补发或节点修改，相关操作继续使用原业务权限和接口。

## 接口

| 方法 | 路径（相对 tickets 根路径） | 内容 |
| --- | --- | --- |
| GET | 空路径 | 分页 `page/size/status/category/keyword`；管理端另支持 `view/memberId/assigneeId` |
| POST | 空路径，仅客户 | `title/category/body/orderId?/subscriptionId?/requestKey/attachmentIds?`，返回工单 ID |
| GET | `/{id}` | 详情及最近 50 条消息；`beforeSeq` 查询更早消息 |
| POST | `/{id}/reply` | `body/internal/status/requestKey/attachmentIds`；客户只能公开回复 |
| POST | `/{id}/action` | `version/action/assigneeId?/priority?/reason?` |
| POST | `/{id}/read` | `lastSeq`，单调递增的已读位置 |
| GET | `/unread`，仅客户 | 所有工单的客服公开回复未读数量 |
| POST | `/attachments` | multipart 字段 `file`，上传未绑定截图并返回文件元数据 |
| GET | `/attachments/{id}` | 鉴权后读取 JPG、PNG、WebP，`Cache-Control: no-store` |
| DELETE | `/attachments/{id}` | 仅上传者可删除尚未发送的截图 |
| GET | `/staff`，仅管理端 | 主管可获取可分配员工 |
| GET | `/{id}/events`，仅管理端 | 最近 50 条操作记录，支持 `beforeId` |

`action` 支持 `claim/assign/priority/close/reopen`；客户只能 close/reopen。普通客服领取需要 assign 权限，转交另外要求主管权限。版本冲突时刷新再操作；领取不会覆盖已有负责人。

创建和回复的 `requestKey` 应在同一次操作的重试中保持一致。相同租户/客户创建或相同发送者回复的相同键仅产生一条记录。截图绑定、消息保存、状态更新与事件日志在事务内提交，绑定失败整体回滚。客户端已保存但响应丢失时可使用同一键重试。

## 运行配置

| 主后台属性 | 默认值 | 用途 |
| --- | --- | --- |
| `speednet.support.max-open` | 5 | 每位客户未关闭工单上限 |
| `speednet.support.create-interval-seconds` | 60 | 两次新建工单间隔 |
| `speednet.support.reply-interval-seconds` | 5 | 客户回复间隔 |
| `speednet.support.attachment-cleanup-ms` | 3600000 | 未发送截图清理间隔 |

同一用户通过会员行锁串行执行新建/回复/重开限制；工单行锁与版本号防止并发覆盖。截图仅允许图片文件头，最多 3 张/消息，最多 9 张暂存/上传者。正文为纯文本，前端转义显示。正文不写入通用接口请求日志。

第一版不包含邮件、自动关闭、响应时限考核、自动分派和满意度评价。

## 验证

新增服务、认证和 HTTP 测试覆盖：会员及租户隔离、客服权限与负责人范围、会话过期/撤销/禁用、内部备注及截图隔离、幂等、事务回滚、消息历史分页、已读并发、限流、关闭重开和并发领取。测试使用独立 H2；V10 完整迁移另在独立临时 MariaDB 执行验证，不写入现有业务数据库。

在主后台根目录运行：

```sh
mvn -pl speednet-module-support -am test -Dtest=TicketServiceTest,TicketIdentityTest,TicketHttpTest -Dsurefire.failIfNoSpecifiedTests=false
```

JDK 25 下若 Mockito 动态附加受环境限制，可用 `-DargLine=-javaagent:/实际路径/mockito-core-版本.jar` 显式加载本地 Mockito agent。客户后台使用自身 JDK 21 环境构建；两个前端分别执行 `pnpm build` 和 `pnpm ts:check && pnpm build:local`。
