# ImageRefine 部署指南

> 本文档面向将 ImageRefine 后端服务部署到京东云 Ubuntu 服务器 + 接入阿里云视觉智能 API 的完整操作流程。

---

## 一、整体架构

```
Android App ──(HTTP)──▶ 京东云 Ubuntu 服务器(代理) ──▶ 阿里云视觉智能 VIAPI
                                  │                            │
                                  │ 上传图片临时存储            │ 读取图片
                                  └──────▶ 阿里云 OSS ◀────────┘
```

- **京东云服务器**：只运行 Python FastAPI，做请求转发，不跑 AI 模型，低配足够（1核1G即可）
- **阿里云 VIAPI**：提供 AI 能力（图像增强、风格迁移、抠图、美颜）
- **阿里云 OSS**：临时中转图片，VIAPI 只接受图片 URL，不接受直传二进制

---

## 二、需要准备的账号和资源

| 序号 | 资源 | 用途 | 去哪获取 |
|------|------|------|---------|
| 1 | 阿里云账号 | 调用 AI API + OSS 存储 | https://www.aliyun.com/ |
| 2 | 阿里云 AccessKey | API 鉴权凭证 | 阿里云控制台 → RAM 访问控制 |
| 3 | 阿里云 OSS Bucket | 临时存储上传的图片 | 阿里云控制台 → 对象存储 OSS |
| 4 | 京东云服务器 | 运行代理后端 | 你已有 |
| 5 | 京东云公网 IP | App 连接后端 | 京东云控制台查看 |

---

## 三、阿里云配置步骤（在浏览器中操作）

### 3.1 创建 RAM 子用户并获取 AccessKey

> **安全原则**：不要使用主账号 AccessKey，创建权限最小化的子用户。

1. 登录阿里云控制台 → 搜索 **RAM 访问控制** → 进入
2. 左侧菜单 → **用户** → **创建用户**
3. 填写：
   - 登录名称：`imagerefine-server`
   - 访问方式：**勾选 OpenAPI 调用访问**
4. 点击确定，页面会显示 `AccessKey ID` 和 `AccessKey Secret`
5. **⚠️ 立即复制保存这两个值**，`AccessKey Secret` 只显示一次

> 这两个值就是 `.env` 配置文件中的：
> - `ALIBABA_ACCESS_KEY_ID` ← AccessKey ID
> - `ALIBABA_ACCESS_KEY_SECRET` ← AccessKey Secret

### 3.2 给子用户授权

1. 在 RAM 用户列表中，找到刚创建的 `imagerefine-server` → 点击 **添加权限**
2. 搜索并添加以下策略：
   - `AliyunVIAPIFullAccess`（视觉智能全部权限）
   - `AliyunOSSFullAccess`（OSS 全部权限）
3. 点击确定

> 如果找不到 `AliyunVIAPIFullAccess`，也可以分别搜索添加：
> - `AliyunImagesegFullAccess`（分割抠图）
> - `AliyunImageenhanFullAccess`（图像增强）
> - `AliyunFacebodyFullAccess`（人脸人体）

### 3.3 开通视觉智能服务

首次调用 VIAPI 接口会自动开通，或手动开通：

1. 访问 https://vision.console.aliyun.com/
2. 分别进入以下服务页面，点击 **开通**：
   - **图像增强**（imageenhan）— 用于智能美化、风格迁移
   - **分割抠图**（imageseg）— 用于背景去除
   - **人脸人体**（facebody）— 用于美颜

> 每个服务每月前 500 次调用免费。

### 3.4 创建 OSS Bucket

1. 阿里云控制台 → 搜索 **对象存储 OSS** → 进入
2. 点击 **创建 Bucket**
3. 填写：
   - **Bucket 名称**：自定义，例如 `imagerefine-temp`（全局唯一）
   - **地域**：`华东2（上海）`（必须选上海，与 VIAPI 同区域，否则无法访问）
   - **存储类型**：标准存储
   - **读写权限**：**私有**（安全，通过签名 URL 访问）
4. 点击确定

> 创建完成后记下：
> - Bucket 名称 → 对应 `.env` 中的 `OSS_BUCKET_NAME`
> - 外网 Endpoint → 对应 `.env` 中的 `OSS_ENDPOINT`（默认为 `oss-cn-shanghai.aliyuncs.com`）

### 3.5（推荐）设置 OSS 生命周期规则自动清理临时文件

1. 进入你的 Bucket → 左侧菜单 **基础设置** → **生命周期**
2. 点击 **设置规则**：
   - 前缀：`imagerefine/tmp/`
   - 过期策略：**1 天后删除**
3. 保存

这样上传的临时图片会在 1 天后自动清理，避免产生不必要的存储费用。

---

## 四、京东云服务器配置步骤

### 4.1 确认服务器环境

通过 SSH 登录你的京东云服务器：

```bash
ssh root@<你的京东云公网IP>
# 或
ssh <你的用户名>@<你的京东云公网IP>
```

检查系统版本和 Python：

```bash
# 查看 Ubuntu 版本
lsb_release -a

# 查看 Python 版本（需要 3.9+）
python3 --version
```

如果 Python 版本低于 3.9 或未安装：

```bash
sudo apt update
sudo apt install -y python3 python3-pip python3-venv
```

### 4.2 安装必要系统工具

```bash
sudo apt update
sudo apt install -y git unzip curl
```

### 4.3 上传代码到服务器

**方式 A：从本机上传（推荐）**

在你的 **Windows 本机** PowerShell 中执行：

```powershell
# 将 server 目录上传到服务器
scp -r D:\program\my_git\GitHub_Projects\ImageRefine\server\ root@<京东云公网IP>:/opt/imagerefine-server/
```

**方式 B：在服务器上用 Git 克隆**

```bash
cd /opt
git clone https://github.com/csapp0903/ImageRefine.git
# 只需要 server 目录
ln -s /opt/ImageRefine/server /opt/imagerefine-server
```

### 4.4 创建配置文件

```bash
cd /opt/imagerefine-server

# 从模板复制
cp .env.example .env

# 编辑配置
nano .env
```

打开 `.env` 后，逐行修改为你的实际值：

```ini
# ========== 必填项（3 个） ==========

# 步骤 3.1 获取的 AccessKey ID
ALIBABA_ACCESS_KEY_ID=LTAI5t****你的实际值****

# 步骤 3.1 获取的 AccessKey Secret
ALIBABA_ACCESS_KEY_SECRET=****你的实际值****

# 步骤 3.4 创建的 Bucket 名称
OSS_BUCKET_NAME=imagerefine-temp

# ========== 通常不需要改（有默认值） ==========

# 阿里云区域 — 如果 Bucket 选的是上海，保持默认即可
ALIBABA_REGION=cn-shanghai

# OSS 外网 Endpoint — 如果 Bucket 选的是上海，保持默认即可
OSS_ENDPOINT=oss-cn-shanghai.aliyuncs.com

# OSS 签名 URL 有效期（秒），默认 1 小时，无需修改
OSS_URL_EXPIRY=3600

# 服务监听地址和端口，通常不需要改
SERVER_HOST=0.0.0.0
SERVER_PORT=8000

# 每分钟每 IP 最大请求数，根据需要调整
RATE_LIMIT_PER_MINUTE=30

# API Token 鉴权 — 留空则不验证，填写后 App 请求必须带此 Token
# 建议设置一个随机字符串，防止他人滥用你的服务器
# 示例: API_TOKEN=my-secret-token-abc123
API_TOKEN=
```

编辑完成后 `Ctrl+O` 保存，`Ctrl+X` 退出。

### 4.5 一键部署

```bash
cd /opt/imagerefine-server

# 授予执行权限
chmod +x deploy.sh stop.sh

# 执行部署
./deploy.sh
```

脚本会自动完成：
1. 检查 Python 版本
2. 创建虚拟环境
3. 安装所有 Python 依赖
4. 验证 `.env` 配置
5. 启动 FastAPI 服务

看到 `✅ 服务启动成功!` 表示部署完成。

### 4.6 验证服务

```bash
# 健康检查
curl http://localhost:8000/api/health
```

应返回：

```json
{"status":"ok","region":"cn-shanghai"}
```

### 4.7 开放防火墙端口

#### 4.7.1 京东云安全组

1. 登录京东云控制台 → 云服务器 → 找到你的实例
2. 安全组 → 添加入站规则：
   - 协议：TCP
   - 端口：8000
   - 源地址：0.0.0.0/0（允许所有）

#### 4.7.2 Ubuntu 系统防火墙（如果启用了 ufw）

```bash
# 查看 ufw 是否启用
sudo ufw status

# 如果是 active，需要放行 8000 端口
sudo ufw allow 8000/tcp
```

#### 4.7.3 外网验证

在你的 Windows 电脑浏览器中访问：

```
http://<京东云公网IP>:8000/api/health
```

能看到 `{"status":"ok"...}` 说明外网可达。

### 4.8（推荐）设置开机自启

```bash
# 复制 systemd 服务文件
sudo cp /opt/imagerefine-server/imagerefine.service /etc/systemd/system/

# 如果服务器登录用户不是 root，需要编辑服务文件修改 User 字段
sudo nano /etc/systemd/system/imagerefine.service
# 将 User=root 改为 User=你的用户名

# 启用并启动
sudo systemctl daemon-reload
sudo systemctl enable imagerefine
sudo systemctl start imagerefine

# 查看状态
sudo systemctl status imagerefine
```

设置后即使服务器重启，服务也会自动运行。

> 注意：使用 systemd 管理后，就不要再用 `deploy.sh` 启动了，用以下命令管理：
> ```bash
> sudo systemctl start imagerefine     # 启动
> sudo systemctl stop imagerefine      # 停止
> sudo systemctl restart imagerefine   # 重启
> sudo journalctl -u imagerefine -f    # 查看日志
> ```

---

## 五、App 端配置

### 5.1 设置服务器地址

在手机 App 中：

1. 打开一张照片进入编辑页面
2. 点击底部 **AI** 标签页
3. 点击 **服务器** 按钮（齿轮图标）
4. 输入你的后端地址：`http://<京东云公网IP>:8000/`
5. 点击确定

### 5.2 如果启用了 API Token 鉴权

当 `.env` 中设置了 `API_TOKEN` 后，需要修改 App 源码中的 [AiConfig.kt](app/src/main/java/com/imagerefine/app/data/remote/AiConfig.kt) 文件，添加 Token：

```kotlin
object AiConfig {
    var BASE_URL = "http://<京东云公网IP>:8000/"
    // ... 其他配置

    /** API 鉴权 Token，与服务器 .env 中的 API_TOKEN 保持一致 */
    var API_TOKEN = "你在.env中设置的API_TOKEN值"
}
```

同时修改 [AiService.kt](app/src/main/java/com/imagerefine/app/data/remote/AiService.kt) 中的 OkHttp 客户端，添加 Token 拦截器：

```kotlin
private fun createClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(AiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(AiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(AiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${AiConfig.API_TOKEN}")
                .build()
            chain.proceed(request)
        }
        .build()
}
```

### 5.3 如果使用 HTTPS（生产环境推荐）

如果你的京东云服务器配置了域名 + SSL 证书（Nginx 反向代理），将 App 中地址改为 `https://your-domain.com/`，并移除 [network_security_config.xml](app/src/main/res/xml/network_security_config.xml) 中的 `cleartextTrafficPermitted` 配置。

---

## 六、所有占位符参数汇总

以下是代码中所有需要根据实际情况替换的占位符：

### 6.1 服务器端 `.env` 文件（3 个必填 + 5 个可选）

| 参数名 | 占位符/默认值 | 说明 | 是否必填 |
|--------|-------------|------|---------|
| `ALIBABA_ACCESS_KEY_ID` | `your-access-key-id` | 阿里云 RAM 子用户的 AccessKey ID | ✅ 必填 |
| `ALIBABA_ACCESS_KEY_SECRET` | `your-access-key-secret` | 阿里云 RAM 子用户的 AccessKey Secret | ✅ 必填 |
| `OSS_BUCKET_NAME` | `your-bucket-name` | 阿里云 OSS Bucket 名称 | ✅ 必填 |
| `ALIBABA_REGION` | `cn-shanghai` | 阿里云区域。Bucket 在上海时保持默认 | 可选 |
| `OSS_ENDPOINT` | `oss-cn-shanghai.aliyuncs.com` | OSS 外网 Endpoint。Bucket 在上海时保持默认 | 可选 |
| `SERVER_PORT` | `8000` | 服务监听端口 | 可选 |
| `RATE_LIMIT_PER_MINUTE` | `30` | 每 IP 每分钟最大请求数 | 可选 |
| `API_TOKEN` | （空） | API 鉴权 Token，留空不验证 | 可选 |

### 6.2 App 端 `AiConfig.kt`（1 个必填）

| 参数 | 当前占位符 | 替换为 | 说明 |
|------|-----------|--------|------|
| `BASE_URL` | `http://10.0.2.2:8000/` | `http://<京东云公网IP>:8000/` | App 内也可通过 UI 修改 |

### 6.3 systemd 服务文件 `imagerefine.service`（1 个可选）

| 参数 | 当前值 | 说明 |
|------|--------|------|
| `User=root` | `root` | 如果不用 root 登录，改为你的 Ubuntu 用户名 |
| `WorkingDirectory` | `/opt/imagerefine-server` | 如果部署路径不同，需要修改 |
| `ExecStart` 中的路径 | `/opt/imagerefine-server/venv/bin/uvicorn` | 同上 |
| `EnvironmentFile` | `/opt/imagerefine-server/.env` | 同上 |

---

## 七、完整操作清单（按顺序执行）

```
Phase 1: 阿里云配置（浏览器操作）
├── [ ] 1. 注册/登录阿里云
├── [ ] 2. 创建 RAM 子用户，获取 AccessKey ID 和 Secret 
├── [ ] 3. 给子用户授权（VIAPI + OSS）
├── [ ] 4. 开通视觉智能三项服务（图像增强、分割抠图、人脸人体）
├── [ ] 5. 创建 OSS Bucket（上海区域、私有权限）
└── [ ] 6. 设置 OSS 生命周期自动清理规则

Phase 2: 京东云服务器部署（SSH 操作）
├── [ ] 7. SSH 登录服务器
├── [ ] 8. 安装 Python 3.9+ 和系统工具
├── [ ] 9. 上传 server/ 代码到 /opt/imagerefine-server/
├── [ ] 10. 复制 .env.example 为 .env，填入 3 个必填参数
├── [ ] 11. 运行 ./deploy.sh 一键部署
├── [ ] 12. curl 健康检查验证
├── [ ] 13. 京东云安全组放行 8000 端口
├── [ ] 14. 外网浏览器验证 http://<公网IP>:8000/api/health
└── [ ] 15. （可选）配置 systemd 开机自启

Phase 3: App 端配置
├── [ ] 16. 编辑页面 → AI Tab → 服务器 → 输入京东云公网IP地址
└── [ ] 17. 测试 AI 功能（先试智能美化，最简单）
```

---

## 八、常见问题排查

### Q1: `deploy.sh` 报错 "配置错误: ALIBABA_ACCESS_KEY_ID 未设置"

`.env` 文件中的必填项没有正确填写。用 `cat .env` 检查内容，确认没有多余的空格或引号。

### Q2: 启动成功但 App 连不上

1. 确认京东云安全组已开放 8000 端口
2. 确认 Ubuntu 防火墙 `sudo ufw status` 没有阻拦
3. 确认 App 中填写的地址格式正确：`http://公网IP:8000/`（末尾带 `/`）
4. 用电脑浏览器先测试 `http://公网IP:8000/api/health`

### Q3: 调用 AI 接口返回 500 错误

查看服务器日志：

```bash
tail -f /opt/imagerefine-server/server.log
# 或者如果用 systemd:
sudo journalctl -u imagerefine -f
```

常见原因：
- 阿里云服务未开通 → 去控制台开通对应服务
- OSS Bucket 区域和 VIAPI 区域不一致 → 都选上海
- AccessKey 权限不足 → 检查 RAM 授权

### Q4: 返回 "请求过于频繁"（429）

`.env` 中 `RATE_LIMIT_PER_MINUTE` 设大一些，例如改为 60。

### Q5: 图片上传超时

- 服务器带宽不够 → 在京东云控制台升级带宽
- 图片太大 → App 端有自动压缩，最大 1920px，85% 质量

### Q6: 如何查看阿里云的调用量和费用？

阿里云控制台 → 视觉智能开放平台 → 左侧 **调用统计** 查看。

---

## 九、费用预估

| 项目 | 免费额度 | 超出费用 | 说明 |
|------|---------|---------|------|
| 图像增强 | 500次/月 | ~¥0.04/次 | 智能美化功能 |
| 风格迁移 | 500次/月 | ~¥0.04/次 | 风格迁移功能 |
| 人体分割 | 500次/月 | ~¥0.02/次 | 智能抠图功能 |
| 人脸美颜 | 500次/月 | ~¥0.04/次 | 美颜功能 |
| OSS 存储 | 5GB 免费 | ¥0.12/GB/月 | 设置自动清理后几乎无费用 |
| 京东云服务器 | — | 你已有 | — |

> 以上为参考价格，以阿里云官网实际定价为准
