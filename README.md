# GlucoSense-lab

实验数据管理与传感器数据可视化平台，面向血糖传感器实验场景，提供批次、受试人员、实验成员、传感器档案、佩戴记录、竞品文件、指尖血数据、每日实验指标和 CGM 传感器读数的统一管理与分析能力。

项目采用前后端分离架构：后端基于 Spring Boot 提供 REST API，前端基于 Vue 3 构建管理端页面，数据库使用 MySQL。系统当前包含 13 个后端 Controller、87 个 REST API、13 张主业务表和 4 张外部传感器相关表。

##在线演示
http://43.143.77.127/
账号:test
密码:123456
## 功能模块

- 用户认证：登录、注册、当前用户信息、登出。
- 权限管理：支持 Admin/User 角色，按 10 个业务模块配置读、写、删权限。
- 首页统计：批次数、人员数、实验数、指尖血数据量统计及操作日志展示。
- 批次管理：批次新增、查询、修改、删除，支持批次号唯一性校验。
- 人员管理：受试人员新增、查询、修改、删除，支持按批次维护人员。
- 实验管理：实验创建、实验成员维护、成员增删、实验记录删除。
- 竞品数据：竞品文件上传、下载、重命名、删除、完整性检查和 Excel 导出。
- 指尖血数据：指尖血数据增删改查、批量删除、条件筛选和 Excel 导出。
- 传感器管理：传感器基础信息维护，支持与人员、批次、传感器详情关联。
- 传感器详情：传感器测试编号、探针编号、响应值、灵敏度、R 值等信息维护，支持批量创建、重复检测和批量删除。
- 佩戴记录：传感器佩戴记录维护，校验同一传感器和同一佩戴位置的未结束记录冲突。
- 实验数据分析：Excel 导入每日实验指标，按人员和实验天数更新 MARD/PARD 数据，并计算平均值。
- 传感器数据可视化：聚合业务库、云端用户库、云端设备库、云端传感器数据库，按用户、批次、人员、日期查询 CGM 数据，并整合竞品数据和指尖血数据。

## 技术栈

### 后端

- Java 8
- Spring Boot 2.7.18
- Spring Web
- Spring JDBC
- MySQL Connector/J
- JJWT 0.11.5
- Spring Security Crypto
- Apache POI 5.2.5
- Maven

### 前端

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Element Plus
- Axios
- ECharts / vue-echarts
- xlsx

### 数据库

- MySQL 5.7/8.x
- 主业务库：`experiment_manage`
- 外部用户库：`cloud_user_db`
- 外部设备库：`cloud_device_db`
- 外部传感器数据库：`cloud_sensor_data_db`

## 项目结构

```text
ExperimentMS-main
├── backend-java/                # Spring Boot 后端
│   ├── src/main/java/com/experimentms
│   │   ├── config/              # Web、CORS、多数据源配置
│   │   ├── controller/          # REST API 控制器
│   │   ├── exception/           # 全局异常处理
│   │   ├── security/            # JWT、鉴权、权限校验
│   │   ├── service/             # 数据库访问和操作日志服务
│   │   └── util/                # 参数、时间、Map 工具
│   ├── src/main/resources/
│   │   └── application.yml      # 后端配置
│   └── pom.xml
├── frontend/                    # Vue 3 前端
│   ├── src/pages/               # 页面模块
│   ├── src/components/          # 通用组件
│   ├── src/services/api.ts      # API 封装
│   ├── src/stores/              # Pinia 状态管理
│   └── package.json
└── database_schemas/            # 数据库建表和模拟数据脚本
    ├── experiment_manage.sql
    ├── external_three_databases.sql
    ├── simulated_data.sql
    └── generate_simulated_data.py
```

## 环境要求

- JDK 8+
- Maven 3.6+
- Node.js 18+
- npm 9+
- MySQL 5.7/8.x

## 数据库初始化

先创建主业务库和 3 个外部库：

```powershell
mysql -uroot -p < database_schemas\experiment_manage.sql
mysql -uroot -p < database_schemas\external_three_databases.sql
```

如需导入模拟数据，可继续执行：

```powershell
mysql -uroot -p < database_schemas\simulated_data.sql
```

模拟数据中的用户密码哈希是占位值，不能直接登录。开发环境可以注册新用户，或执行下面的 SQL 初始化一个管理员账号：

```sql
USE experiment_manage;

INSERT INTO users (username, password_hash, role, createTime, updateTime)
VALUES (
  'admin',
  '$2b$12$dMsyzgd.zRm.rztTUkZ8neYtHFa1fUTyuMwkYRPP1t98enGpYZ3lu',
  'Admin',
  NOW(),
  NOW()
)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  role = 'Admin',
  updateTime = NOW();
```

默认账号：

```text
username: admin
password: 123456
```

> 上面的账号仅建议用于本地开发环境。正式环境请修改默认密码，并使用更安全的 `JWT_SECRET`。

## 后端启动

进入后端目录：

```powershell
cd backend-java
```

构建项目：

```powershell
mvn clean package -DskipTests
```

启动服务：

```powershell
java -jar target\experimentms-backend-1.0.0.jar
```

默认后端地址：

```text
http://localhost:8000
```

健康检查：

```text
http://localhost:8000/health
```

## 前端启动

进入前端目录：

```powershell
cd frontend
```

安装依赖：

```powershell
npm install
```

启动开发服务：

```powershell
npm run dev
```

默认前端地址：

```text
http://localhost:5173
```

前端 API 基础地址写在 `frontend/src/services/api.ts` 中，默认指向：

```text
http://localhost:8000
```

如果后端端口变化，需要同步修改该地址，或改造成环境变量配置。

## 后端配置

后端配置文件位于 `backend-java/src/main/resources/application.yml`。常用环境变量如下：

| 变量名                    | 默认值                 | 说明                   |
| ------------------------- | ---------------------- | ---------------------- |
| `DB_HOST`                 | `localhost`            | 主业务库地址           |
| `DB_PORT`                 | `3306`                 | 主业务库端口           |
| `DB_NAME`                 | `experiment_manage`    | 主业务库名称           |
| `DB_USER`                 | `root`                 | 主业务库用户名         |
| `DB_PASSWORD`             | `123456`               | 主业务库密码           |
| `JWT_SECRET`              | 配置文件内默认值       | JWT 签名密钥           |
| `JWT_EXPIRATION_MINUTES`  | `30`                   | Token 有效期，单位分钟 |
| `UPLOADS_DIR`             | `uploads`              | 上传文件目录           |
| `DOWNLOADS_DIR`           | `downloads`            | 下载文件目录           |
| `EXTERNAL_DB_HOST`        | `localhost`            | 外部库地址             |
| `EXTERNAL_DB_PORT`        | `3306`                 | 外部库端口             |
| `EXTERNAL_DB_USER`        | `root`                 | 外部库用户名           |
| `EXTERNAL_DB_PASSWORD`    | `123456`               | 外部库密码             |
| `EXTERNAL_USER_DB`        | `cloud_user_db`        | 云端用户库             |
| `EXTERNAL_DEVICE_DB`      | `cloud_device_db`      | 云端设备库             |
| `EXTERNAL_SENSOR_DATA_DB` | `cloud_sensor_data_db` | 云端传感器数据库       |

文件上传限制：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB
```

## API 概览

| 模块         | 路径前缀               | 说明                                           |
| ------------ | ---------------------- | ---------------------------------------------- |
| 认证与用户   | `/api/auth`            | 登录、注册、用户管理、权限分配                 |
| 批次管理     | `/api/batches`         | 批次增删改查                                   |
| 人员管理     | `/api/persons`         | 人员增删改查、批次人员查询                     |
| 实验管理     | `/api/experiments`     | 实验及实验成员维护                             |
| 竞品文件     | `/api/competitorFiles` | 文件上传、下载、重命名、删除、导出             |
| 指尖血数据   | `/api/fingerBloodData` | 指尖血数据维护、批量删除、Excel 导出           |
| 传感器管理   | `/api/sensors`         | 传感器信息维护                                 |
| 传感器详情   | `/api/sensorDetails`   | 传感器详情维护、批量创建、重复检测             |
| 佩戴记录     | `/api/wearRecords`     | 佩戴记录维护和冲突校验                         |
| 实验数据分析 | `/api/experimentData`  | 每日实验指标导入、查询、统计                   |
| 传感器可视化 | `/api/sensor-data`     | CGM 数据聚合、竞品数据、指尖血数据、Excel 下载 |
| 操作日志     | `/api/activities`      | 操作记录查询和创建                             |
| 首页统计     | `/api/stats/dashboard` | 首页统计指标                                   |

除 `/api/auth/login` 和 `/api/auth/register` 外，其他 `/api/**` 接口均需要在请求头中携带 JWT：

```text
Authorization: Bearer <access_token>
```

## 核心业务规则

- 批次号唯一，删除批次前会检查实验、竞品文件、指尖血、传感器等关联数据。
- 传感器测试编号和探针编号唯一，批量创建时会提前检测重复数据。
- 创建实验时至少需要 1 名实验成员，实验成员通过中间表维护。
- 同一传感器只能存在 1 条未结束佩戴记录。
- 同一人员同一佩戴位置只能存在 1 条未结束佩戴记录。
- 佩戴记录结束后，会同步回写传感器结束时间和结束原因。
- 实验指标导入时，按 `person_id + experiment_day` 判断新增或更新。
- 传感器可视化按云端设备佩戴时间筛选 15 天内的 CGM 读数。

## 前端页面

- `/login`：登录/注册
- `/dashboard`：首页统计
- `/batches`：批次管理
- `/persons`：人员管理
- `/experiments`：实验管理
- `/competitorData`：竞品数据
- `/fingerBloodData`：指尖血数据
- `/sensors`：传感器管理
- `/sensorDetails`：传感器详情
- `/wearRecords`：佩戴记录
- `/sensorDataVisualization`：传感器数据可视化
- `/experimentDataAnalysis`：实验数据分析
- `/users`：用户管理，管理员可见

## 构建命令

后端：

```powershell
cd backend-java
mvn clean package -DskipTests
```

前端：

```powershell
cd frontend
npm run build
```

前端类型检查：

```powershell
cd frontend
npm run check
```

前端代码检查：

```powershell
cd frontend
npm run lint
```

