# mok-operation-log-spring-boot-starter

基于 **AOP + RabbitMQ** 的异步操作日志自动记录 Starter，支持 **MySQL** 和 **Elasticsearch** 双后端存储。

## 特性

- 🔌 **零侵入**：一个 `@OperationLog` 注解即可记录操作日志
- 🔀 **双后端**：支持 MySQL（MyBatis-Plus）和 Elasticsearch，一行配置切换
- 📨 **异步解耦**：通过 RabbitMQ 异步发送，不阻塞主业务流程
- 🧩 **SPI 可扩展**：操作人获取、参数脱敏均可自定义实现
- 🔄 **重试 + 死信**：内置 MQ 重试机制（最多 3 次），失败消息进入死信队列
- 🛡️ **参数脱敏**：自动过滤 password/token/secret 等敏感字段
- 🎯 **Spring Boot 3.x 原生**：基于 AutoConfiguration，零 XML 配置

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>top.jiangmok</groupId>
    <artifactId>mok-operation-log-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 选择存储后端

**MySQL（默认）**：需要同时引入 MyBatis-Plus 和 MySQL 驱动。

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

执行建表语句（位于 `src/main/resources/sql/sys_operation_log.sql`）。

**Elasticsearch**：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

### 3. 配置

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

mok:
  operation-log:
    enabled: true              # 是否启用，默认 true
    save-location: mysql       # 存储位置：mysql | es，默认 mysql
    record-get: false          # 是否记录 GET 请求，默认 true
    max-content-length: 2000   # 参数/响应截断长度，默认 2000
```

### 4. 实现操作人解析器

```java
@Component
public class MyOperatorResolver implements OperatorResolver {

    @Override
    public String getOperatorId() {
        return StpUtil.getLoginIdAsString(); // 或其他认证框架
    }

    @Override
    public String getOperatorName() {
        return "当前用户名";
    }

    @Override
    public String getOperatorType() {
        return "ADMIN";
    }

    @Override
    public String getDeptName() {
        return "技术部"; // 可选，返回 null 亦可
    }
}
```

> 如果项目使用 **Sa-Token** 或 **Spring Security**，Starter 会自动检测并创建对应的 OperatorResolver，无需手动实现。

### 5. 使用注解

```java
@RestController
@RequestMapping("/user")
public class UserController {

    @OperationLog(title = "新增用户", businessType = BusinessType.INSERT)
    @PostMapping
    public R createUser(@RequestBody User user) {
        // 业务逻辑
    }

    @OperationLog(title = "删除用户", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R deleteUser(@PathVariable String id) {
        // 业务逻辑
    }
}
```

## 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mok.operation-log.enabled` | Boolean | `true` | 是否启用操作日志 |
| `mok.operation-log.save-location` | String | `mysql` | 存储位置：`mysql` 或 `es` |
| `mok.operation-log.record-get` | Boolean | `true` | 是否记录 GET 请求 |
| `mok.operation-log.max-content-length` | Integer | `2000` | 参数/响应超过此长度自动截断 |

## 业务类型

| 枚举 | 含义 |
|------|------|
| `LOGIN` | 登录 |
| `LOGOUT` | 登出 |
| `QUERY` | 查询 |
| `INSERT` | 新增 |
| `UPDATE` | 修改 |
| `DELETE` | 删除 |
| `GRANT` | 授权 |
| `EXPORT` | 导出 |
| `IMPORT` | 导入 |
| `FORCE` | 强退 |
| `CLEAN` | 清空数据 |
| `OTHER` | 其它 |

## 扩展点

### 自定义参数脱敏

默认脱敏规则会过滤 `password`、`token`、`secret`、`key`、`pwd`、`pass`、`credential` 等字段。

```java
@Component
public class MyParamDesensitizer implements ParamDesensitizer {
    @Override
    public String desensitize(String jsonParam) {
        // 自定义脱敏逻辑
        return jsonParam.replaceAll("手机号的", "***");
    }
}
```

### 自定义存储实现

```java
@Component
public class MyMongoOperationLogService implements OperationLogService {
    // 实现所有方法，存 MongoDB / PostgreSQL / 文件 ……
}
```

Starter 发现容器中已有 `OperationLogService` Bean 时会自动跳过默认实现。

### 自定义死信处理

继承 `OperationLogConsumer`，覆盖 `onSaveFailed` 方法：

```java
@Component
public class MyOperationLogConsumer extends OperationLogConsumer {

    public MyOperationLogConsumer(OperationLogService service) {
        super(service);
    }

    @Override
    protected void onSaveFailed(Message message) {
        // 发钉钉告警、写数据库……
    }
}
```

## 架构

```
用户请求
  │
  ▼
@OperationLog 注解的方法
  │
  ▼
OperationLogAspect（AOP 切面）
  │ 收集方法信息、参数（脱敏后）、操作人
  │ 构建 OperationLogMessage
  ▼
RabbitMQ（异步发送）
  │
  ▼
OperationLogConsumer（消费者）
  │ 幂等检查 → 重试机制 → 死信兜底
  ▼
OperationLogService
  ├── OperationLogMySqlServiceImpl（MySQL + MyBatis-Plus）
  └── OperationLogESServiceImpl（Elasticsearch）
```

## 项目结构

```
src/main/java/top/jiangmok/operationlog/
├── annotation/OperationLog.java          # @OperationLog 注解
├── aspect/OperationLogAspect.java        # AOP 切面
├── autoconfigure/                        # 自动配置（3 个）
├── config/OperationLogProperties.java    # 配置属性
├── constant/OperationLogMQConstant.java  # MQ 常量
├── consumer/OperationLogConsumer.java    # MQ 消费者
├── desensitize/                          # 参数脱敏 SPI
├── entity/OperationLogEntity.java        # 实体
├── enums/BusinessType.java               # 业务类型枚举
├── mapper/OperationLogMapper.java        # MyBatis-Plus Mapper
├── message/OperationLogMessage.java      # MQ 消息体
├── operator/                             # 操作人解析器 SPI
├── repository/OperationLogRepository.java # ES Repository
└── service/                              # 存储服务
```

## 依赖说明

| 依赖 | 必选 | 说明 |
|------|:--:|------|
| spring-boot-starter-aop | ✅ | AOP 切面 |
| spring-boot-starter-amqp | ✅ | RabbitMQ |
| spring-boot-starter-web | ✅ | HttpServletRequest |
| jackson-databind | ✅ | JSON 序列化 |
| hutool-all | ✅ | UUID 生成等工具 |
| mybatis-plus-spring-boot3-starter | ❌ | MySQL 存储时需要 |
| mysql-connector-j | ❌ | MySQL 存储时需要 |
| spring-boot-starter-data-elasticsearch | ❌ | ES 存储时需要 |
| sa-token-spring-boot3-starter | ❌ | 自动解析 Sa-Token 操作人时需要 |
| spring-boot-starter-security | ❌ | 自动解析 Security 操作人时需要 |

## License

Apache License 2.0
