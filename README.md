# FreeWillase (酶信息平台)

酶有自由意志，但数据需要管理。

## 快速开始 (一键启动)

为了获得最佳开发体验，我们提供了集成启动脚本。只需运行根目录下的：
- **`FreeWillase_Launcher.bat`** (Windows)

该脚本将自动完成以下操作：
1. 启动 Docker MySQL 容器。
2. 启动 MiniFold-v1 AI 预测引擎 (Python)。
3. 启动 Spring Boot 后端。
4. 启动 Vue3 前端并进入开发模式。

> "自由意志已夺回，所有引擎已就绪。"

## 技术栈
- **后端**: Spring Boot 3.3.1, MyBatis-Plus, Flyway, MySQL
- **前端**: Vue3, Tailwind CSS, Molstar (3D渲染)

## 团队协作与数据库设置

为了方便团队协作，我们采用了以下方案管理数据库：

### 1. 自动数据库迁移 (Flyway)
我们使用 Flyway 来管理数据库版本。所有的表结构变更都保存在 `src/main/resources/db/migration` 目录下。
- 当你启动后端服务时，Flyway 会自动检查并执行尚未运行的 SQL 脚本。
- **注意**: 请不要直接修改已有的 `V__xxx.sql` 文件，如果需要修改表结构，请创建一个新的版本文件（如 `V2__add_new_table.sql`）。

### 2. 本地数据库环境 (Docker Compose)
如果你本地没有 MySQL 或者不想手动安装，可以使用 Docker 一键启动：
```bash
docker-compose up -d
```
这会启动一个 MySQL 8.0 实例，数据库名为 `freewillase`，root 密码为 `root`，端口映射到本地 `3306`。

### 3. 配置说明
项目使用环境变量来管理敏感信息。默认配置在 `src/main/resources/application.properties` 中。
你可以通过以下方式覆盖默认配置：
- 在系统环境变量中设置 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`。
- 或者参考 `application.properties.example` 修改你的本地配置。

## 开发启动

### 后端
```bash
./mvnw spring-boot:run
```

### 前端
```bash
cd frontend
npm install
npm run dev
```

## 贡献指南
1. Fork 本仓库。
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)。
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)。
4. 推送到分支 (`git push origin feature/AmazingFeature`)。
5. 开启 Pull Request。
