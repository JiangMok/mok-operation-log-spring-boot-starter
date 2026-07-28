# mok-operation-log-spring-boot-starter

基于 **AOP + 可插拔异步策略**的操作日志自动记录 Starter，支持 **MySQL** 和 **Elasticsearch** 双后端存储。

## 特性

- 🔌 **零侵入**：一个 `@OperationLog` 注解即可记录操作日志
- ⚡ **默认轻量**：内置 `@Async` 线程池异步写入，零外部中间件依赖
- 🔀 **双后端**：支持 MySQL（MyBatis-Plus）和 Elasticsearch，一行配置切换
- 📨 **可选 MQ**：可切换至 RabbitMQ 策略，完整保留重试/DLX/幂等逻辑
- 🧩 **SPI 可扩展**：操作人获取、参数脱敏、异步发送均可自定义实现
- 🛡️ **参数脱敏**：自动过滤 password/token/secret 等敏感字段
- 🎯 **Spring Boot 3.x 原生**：基于 AutoConfiguration，零 XML 配置

---

## 快速开始

### 引入 Starter

```xml
<dependency>
    <groupId>top.jiangmok</groupId>
    <artifactId>mok-operation-log-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

> 执行建表语句 `src/main/resources/sql/sys_operation_log.sql`

### 场景一：最小配置（最懒人）

数据源配好，yaml **一行都不写**，直接用注解。

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

此时实际生效的配置（全部是默认值，你啥也没写）：

| 属性 | 实际值 | 含义 |
|------|--------|------|
| `async-strategy` | `async` | 用 @Async 线程池写日志 |
| `save-location` | `mysql` | 存在 MySQL |
| `record-get` | `true` | GET 请求也记录 |
| `max-content-length` | `2000` | 超长内容自动截断 |
| `task-executor.core-size` | `2` | 核心线程 2 个 |
| `task-executor.max-size` | `4` | 最多扩到 4 个 |
| `task-executor.queue-capacity` | `200` | 队列 200 个 |

**不用装 RabbitMQ。** 请求进来 → 切面拦截 → 丢给线程池 → 后台写库，主线程不阻塞。

---

### 场景二：换成 Elasticsearch

pom.xml 换一个依赖，yaml 改一行：

```xml
<!-- 替换掉 mybatis-plus 和 mysql-connector-j -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

```yaml
mok:
  operation-log:
    save-location: es          # 就改这一行
```

其余不变，注解照用。

---

### 场景三：不记录 GET 请求

只想记录增删改，GET 请求跳过：

```yaml
mok:
  operation-log:
    record-get: false          # 加这一行
```

---

### 场景四：日志很多，调大线程池

如果你的业务日志量很大（比如频繁的批量操作），默认的 2 核 4 线程不够，可以调大：

```yaml
mok:
  operation-log:
    task-executor:
      core-size: 5             # 常驻 5 个线程
      max-size: 10             # 忙时最多 10 个
      queue-capacity: 500      # 排队长 500
```

> 提示：如果 500 都满了，调用方线程会自己执行（CallerRunsPolicy 背压降级），不会丢任务。如果这还不够，该考虑切 RabbitMQ 了。

---

### 场景五：切 RabbitMQ（高可靠场景）

日志不能丢（审计合规），需要重试 + 死信兜底。

pom.xml 加一个依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

yaml 改一行，加 RabbitMQ 连接信息：

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

mok:
  operation-log:
    async-strategy: rabbitmq   # 就改这一行
```

Starter 自动注册交换机、队列、绑定、死信队列全套基础设施。Consumer 保留重试（最多 3 次）+ 死信兜底 + 幂等检查。

---

### 小结

```
场景一：啥也不配       → pom 引入，注解直接用
场景二：换 ES          → yaml 改一行
场景三：跳过 GET       → yaml 加一行
场景四：调线程池       → yaml 加三行
场景五：切 RabbitMQ   → pom 加依赖，yaml 改一行 + MQ 连接信息
```

所有属性都有默认值，**你只改需要改的**。

---

## 完整配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mok.operation-log.enabled` | Boolean | `true` | 是否启用操作日志 |
| `mok.operation-log.async-strategy` | String | `async` | `async`（默认）或 `rabbitmq` |
| `mok.operation-log.save-location` | String | `mysql` | 存储位置：`mysql` 或 `es` |
| `mok.operation-log.record-get` | Boolean | `true` | 是否记录 GET 请求 |
| `mok.operation-log.max-content-length` | Integer | `2000` | 参数/响应超过此长度自动截断 |
| `mok.operation-log.task-executor.core-size` | int | `2` | 核心线程数 |
| `mok.operation-log.task-executor.max-size` | int | `4` | 最大线程数 |
| `mok.operation-log.task-executor.queue-capacity` | int | `200` | 任务队列容量 |

---

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

---

## 操作人解析器（可选）

如果项目使用 **Sa-Token** 或 **Spring Security**，Starter 会自动检测并创建对应的 OperatorResolver，无需手动实现。

手动实现示例：

```java
@Component
public class MyOperatorResolver implements OperatorResolver {

    @Override
    public String getOperatorId() {
        return StpUtil.getLoginIdAsString();
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

---

## 扩展点

### 自定义参数脱敏

默认脱敏规则过滤字段：`password`、`token`、`secret`、`key`、`pwd`、`pass`、`credential`。

```java
@Component
public class MyParamDesensitizer implements ParamDesensitizer {
    @Override
    public String desensitize(String jsonParam) {
        return jsonParam.replaceAll("手机号的", "***");
    }
}
```

### 自定义存储

```java
@Component
public class MyMongoOperationLogService implements OperationLogService {
    // 实现所有方法，存 MongoDB / PostgreSQL……
}
```

容器已有 `OperationLogService` 时自动跳过默认实现。

### 自定义发送器（换消息队列）

```java
@Component
public class MyKafkaSender implements OperationLogAsyncSender {
    @Override
    public void send(OperationLogMessage message) {
        // 投递到 Kafka / RocketMQ……
    }
}
```

### 自定义死信处理（仅 RabbitMQ 策略）

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

---

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
OperationLogAsyncSender.send(message)
  │
  ├── async(默认) ──→ @Async 线程池 ──→ 幂等检查 ──→ Service.save()
  │     零外部依赖                        去重后写入 MySQL/ES
  │
  └── rabbitmq(可选) ──→ RabbitMQ ──→ Consumer(retry+DLX+幂等)
        需引入 amqp                    完整保留所有 MQ 逻辑
  ▼
OperationLogService
  ├── OperationLogMySqlServiceImpl（MySQL + MyBatis-Plus）
  └── OperationLogESServiceImpl（Elasticsearch）
```

---

## 项目结构

```
src/main/java/top/jiangmok/operationlog/
├── annotation/OperationLog.java          # @OperationLog 注解
├── aspect/OperationLogAspect.java        # AOP 切面
├── autoconfigure/                        # 自动配置（3 个）
├── config/OperationLogProperties.java    # 配置属性
├── constant/OperationLogMQConstant.java  # MQ 常量
├── consumer/OperationLogConsumer.java    # MQ 消费者（rabbitmq 策略）
├── desensitize/                          # 参数脱敏 SPI
├── entity/OperationLogEntity.java        # 实体
├── enums/BusinessType.java               # 业务类型枚举
├── mapper/OperationLogMapper.java        # MyBatis-Plus Mapper
├── message/OperationLogMessage.java      # 消息体
├── operator/                             # 操作人解析器 SPI
├── repository/OperationLogRepository.java # ES Repository
├── sender/                               # 异步发送器 SPI
│   └── impl/
│       ├── AsyncOperationLogSender.java  # @Async 默认实现
│       └── RabbitMQAsyncSender.java      # RabbitMQ 实现
├── service/                              # 存储服务
└── util/IdGenerator.java                 # ID 生成工具
```

---

## 依赖说明

| 依赖 | 必选 | 说明 |
|------|:--:|------|
| spring-boot-starter-aop | ✅ | AOP 切面 |
| spring-boot-starter-web | ✅ | HttpServletRequest |
| jackson-databind | ✅ | JSON 序列化 |
| spring-boot-starter-amqp | ❌ | RabbitMQ 策略时需要 |
| mybatis-plus-spring-boot3-starter | ❌ | MySQL 存储时需要 |
| mysql-connector-j | ❌ | MySQL 存储时需要 |
| spring-boot-starter-data-elasticsearch | ❌ | ES 存储时需要 |
| sa-token-spring-boot3-starter | ❌ | 自动解析 Sa-Token 操作人时需要 |
| spring-boot-starter-security | ❌ | 自动解析 Security 操作人时需要 |

## License

Apache License 2.0
