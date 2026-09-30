# SpeedNet 客户前台

Vue 3 + TypeScript + Element Plus 前台。现阶段包含邮箱验证码注册、邮箱密码登录，以及参考仓库内 v2board 主题布局的客户仪表盘。

## 运行

先启动 `speed_custom_backend`（默认端口 `48081`），再运行：

```bash
pnpm install
pnpm dev
```

打开 `http://localhost:5173`。Vite 将 `/custom-api` 转发到后端。构建命令：`pnpm build`。

注册、登录成功后进入仪表盘。套餐、节点、订单、工单和流量区域目前是导航占位，后续接入真实业务接口。
