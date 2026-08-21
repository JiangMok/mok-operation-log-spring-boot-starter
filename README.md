# mok-operation-log-spring-boot-starter

基于 **AOP + 可插拔异步策略**的操作日志自动记录 Starter，支持 **文件**、**MySQL** 和 **Elasticsearch** 三后端存储。

## 版本状态

- Maven Central 最新正式版：`1.0.1`
- 当前开发版：`1.1.0-SNAPSHOT`
- 构建与测试基线：Java 17、Spring Boot 3.4.5

`1.1.0-SNAPSHOT` 尚未发布到 Maven Central，使用前需要先克隆本仓库并执行 `mvn clean install`。

## 特性

- 🔌 **零侵入**：一个 `@OperationLog` 注解即可记录操作日志
- 📁 **零外部基础设施起步**：默认文件存储（JSON Lines），不需要数据库、ES 或 MQ
- ⚡ **默认轻量**：内置 `@Async` 线程池异步写入，纯 JDK + Jackson
- 🔀 **三后端**：文件 → MySQL → ES，按需升级，一行配置切换
- 📨 **可选 MQ**：可切换至 RabbitMQ 策略，完整保留重试/DLX/幂等逻辑
- 🧩 **SPI 可扩展**：操作人获取、参数脱敏、异步发送均可自定义实现
- 🛡️ **参数脱敏**：自动过滤 password/token/secret 等敏感字段
- 🧾 **业务失败分类**：业务校验异常自动记为"业务失败"（status=2），与系统异常（status=1）区分统计
- 📄 **标准分页结果**：内置存储返回记录、总数、页码和每页大小
- 👤 **完整操作人快照**：支持 ID、名称、类型和部门，并可一次性解析
- 🎯 **Spring Boot 3.x 原生**：基于 AutoConfiguration，零 XML 配置

---

## 快速开始

### 使用 Maven Central 正式版

```xml
<dependency>
    <groupId>top.jiangmok</groupId>
    <artifactId>mok-operation-log-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

### 使用 1.1.0-SNAPSHOT

先在本仓库执行：

```bash
mvn clean install
```

接入方使用：

```xml
<dependency>
    <groupId>top.jiangmok</groupId>
    <artifactId>mok-operation-log-spring-boot-starter</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

默认文件模式不需要 MySQL、Elasticsearch 或 RabbitMQ。Web 应用通常已经提供 Spring Web；其他可选后端依赖由接入方按需引入。

### 场景一：最小配置

依赖引入后，yaml **一行都不写**，直接用注解：

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

日志自动写入 `./logs/operation-logs/operation-log-{日期}.jsonl`，按天滚动，保留 90 天。

此时实际生效的配置（全部是默认值，你啥也没写）：

| 属性 | 实际值 | 含义 |
|------|--------|------|
| `save-location` | `file` | 存储到文件 |
| `async-strategy` | `async` | 用 @Async 线程池写日志 |
| `record-get` | `true` | GET 请求也记录 |
| `max-content-length` | `2000` | 超长内容自动截断 |
| `file.log-dir` | `./logs/operation-logs` | 日志文件目录 |
| `file.rollover` | `daily` | 按天滚动 |
| `file.max-retention-days` | `90` | 保留 90 天 |
| `task-executor.core-size` | `2` | 核心线程 2 个 |
| `task-executor.max-size` | `4` | 最多扩到 4 个 |
| `task-executor.queue-capacity` | `200` | 队列 200 个 |
| `startup-print` | `true` | 启动时打印 banner |

**不用装数据库、不用装 MQ。** 请求进来 → 切面拦截 → 丢给线程池 → 追加写入 JSONL 文件，主线程不阻塞。

---

### 场景二：切换到 MySQL

数据量上来了，想用 MySQL 做条件查询。

pom.xml 加两个依赖，yaml 改一行：

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

```yaml
mok:
  operation-log:
    save-location: mysql        # 就改这一行
```

> 执行以下建表语句（亦可从 jar 包 `sql/` 目录提取 `mok_operation_log.sql`）：
>
> ```sql
> CREATE TABLE IF NOT EXISTS `mok_operation_log` (
>     `id`             VARCHAR(64)  NOT NULL COMMENT '日志主键',
>     `title`          VARCHAR(100) DEFAULT '' COMMENT '操作标题',
>     `business_type`  VARCHAR(50)  DEFAULT '' COMMENT '业务类型',
>     `method`         VARCHAR(255) DEFAULT '' COMMENT '方法名(类名.方法名)',
>     `request_method` VARCHAR(20)  DEFAULT '' COMMENT '请求方式(GET/POST等)',
>     `operator_type`  VARCHAR(50)  DEFAULT '' COMMENT '操作人类型',
>     `operator_id`    VARCHAR(64)  DEFAULT '' COMMENT '操作人ID',
>     `operator_name`  VARCHAR(100) DEFAULT '' COMMENT '操作人姓名',
>     `dept_name`      VARCHAR(100) DEFAULT '' COMMENT '部门名称',
>     `oper_url`       VARCHAR(500) DEFAULT '' COMMENT '请求URL',
>     `oper_ip`        VARCHAR(50)  DEFAULT '' COMMENT '操作IP',
>     `oper_location`  VARCHAR(100) DEFAULT '' COMMENT '操作地点',
>     `oper_param`     TEXT         COMMENT '请求参数',
>     `json_result`    TEXT         COMMENT '响应结果',
>     `status`         TINYINT      DEFAULT 0 COMMENT '状态(0成功 1失败 2业务失败)',
>     `error_msg`      TEXT         COMMENT '错误信息',
>     `oper_time`      DATETIME     COMMENT '操作时间',
>     `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
>     `retry_count`    INT          DEFAULT 0 COMMENT 'MQ重试次数',
>     PRIMARY KEY (`id`),
>     INDEX `idx_oper_time` (`oper_time`),
>     INDEX `idx_operator_name` (`operator_name`),
>     INDEX `idx_status` (`status`),
>     INDEX `idx_business_type` (`business_type`)
> ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';
> ```

---

### 场景三：切换到 Elasticsearch

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

```yaml
mok:
  operation-log:
    save-location: es           # 就改这一行
```

---

### 场景四：不记录 GET 请求

只想记录增删改，GET 请求跳过：

```yaml
mok:
  operation-log:
    record-get: false           # 加这一行
```

---

### 场景五：日志很多，调大线程池

```yaml
mok:
  operation-log:
    task-executor:
      core-size: 5              # 常驻 5 个线程
      max-size: 10              # 忙时最多 10 个
      queue-capacity: 500       # 排队长 500
```

> 提示：如果 500 都满了，调用方线程会自己执行（CallerRunsPolicy 背压降级），不会丢任务。如果这还不够，该考虑切 RabbitMQ 了。

---

### 场景六：切 RabbitMQ（高可靠场景）

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
    async-strategy: rabbitmq    # 就改这一行
```

Starter 自动注册交换机、队列、绑定和死信队列。保存失败时通过消息头记录重试次数并重新投递，最多重试 3 次，超过上限后进入死信队列；重复消息通过日志 ID 幂等检查直接确认。

---

### 小结

```
场景一：啥也不配       → pom 引入，注解直接用（文件存储）
场景二：换 MySQL       → yaml 改一行 + pom 加两个依赖
场景三：换 ES          → yaml 改一行 + pom 换依赖
场景四：跳过 GET       → yaml 加一行
场景五：调线程池       → yaml 加三行
场景六：切 RabbitMQ   → pom 加依赖，yaml 改一行 + MQ 连接信息
```

所有属性都有默认值，**你只改需要改的**。

---

## 完整配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mok.operation-log.enabled` | Boolean | `true` | 是否启用操作日志，设 `false` 可完全关闭 |
| `mok.operation-log.save-location` | String | `file` | 存储位置：`file`、`mysql` 或 `es` |
| `mok.operation-log.async-strategy` | String | `async` | `async`（默认）或 `rabbitmq` |
| `mok.operation-log.record-get` | Boolean | `true` | 是否记录 GET 请求 |
| `mok.operation-log.max-content-length` | Integer | `2000` | 参数/响应超过此长度自动截断 |
| `mok.operation-log.startup-print` | Boolean | `true` | 启动时是否打印 banner，设 `false` 可静默启动 |
| `mok.operation-log.business-exceptions` | List\<String\> | 空 | 业务异常全限定类名列表（子类自动匹配），命中记为"业务失败"而非"失败" |
| `mok.operation-log.file.log-dir` | String | `./logs/operation-logs` | 日志文件目录（仅 file 模式） |
| `mok.operation-log.file.rollover` | String | `daily` | 滚动策略：`daily`（按天）或 `none`（单文件） |
| `mok.operation-log.file.max-retention-days` | int | `90` | 最大保留天数（仅 daily 模式） |
| `mok.operation-log.task-executor.core-size` | int | `2` | 核心线程数 |
| `mok.operation-log.task-executor.max-size` | int | `4` | 最大线程数 |
| `mok.operation-log.task-executor.queue-capacity` | int | `200` | 任务队列容量 |

---

## 存储后端对比

| | 文件（默认） | MySQL | Elasticsearch |
|------|:--:|:--:|:--:|
| 额外依赖 | 无 | MyBatis-Plus + MySQL 驱动 | spring-boot-starter-data-elasticsearch |
| 安装配置 | 无 | 数据源 + 建表 | ES 集群连接 |
| 写入性能 | 极快（追加写） | 快 | 快 |
| 查询能力 | title/操作人/URL 模糊匹配 + 状态/类型/时间范围过滤 | 完整 SQL | 全文检索 |
| 适用规模 | 开发/小项目 | 中小型 | 中大型 |
| 运维成本 | 零 | 中 | 高 |

> **文件存储亮点（`OperationLogFileServiceImpl`）**  
> - **并发安全**：`ConcurrentHashMap` 按文件加锁；同一文件写入会短暂串行，避免内容交叉或丢失
> - **自动清理**：超出 `file.max-retention-days` 的文件在写入时自动删除，无需外部定时任务  
> - **时间范围优化**：查询时按文件名中的日期自动缩小扫描范围，避免全量遍历  
> - **内容截断**：写入时自动按 `max-content-length` 截断过长参数/响应，防止单行爆炸  
> - **零依赖**：纯 JDK NIO + Jackson，无需任何数据库或中间件

---

## 分页查询

`OperationLogService` 保留原有 `pageQuery()` 列表接口，并新增带总数的分页接口：

```java
OperationLogPageResult result = operationLogService.pageQueryResult(
        pageNum, pageSize, keyword, conditions);
```

分页结果包含 `records`、`total`、`pageNum` 和 `pageSize`，接入方可以转换成自己的统一响应结构。内置 MySQL、文件和 Elasticsearch 存储都会返回准确总数。自定义 `OperationLogService` 若需要准确总数，应覆盖 `pageQueryResult()`；接口默认实现只用于兼容旧实现。

## 注解参数

`@OperationLog` 完整参数：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `title` | String | `""` | 接口标题 |
| `businessType` | BusinessType | `OTHER` | 操作类型 |
| `saveRequestParam` | boolean | `true` | 是否保存请求参数，设 `false` 可跳过敏感接口的入参记录 |
| `saveResponseData` | boolean | `true` | 是否保存响应数据，设 `false` 可减小日志体积 |
| `businessExceptions` | Class\<? extends Throwable\>[] | `{}` | 业务异常类型（子类自动匹配），与全局配置取并集，抛出即记为"业务失败" |

示例：

```java
// 只记标题和类型，不记入参和响应（适合敏感接口）
@OperationLog(title = "用户登录", businessType = BusinessType.LOGIN,
              saveRequestParam = false, saveResponseData = false)
@PostMapping("/login")
public R login(@RequestBody LoginDTO dto) { ... }
```

---

## 业务失败分类

业务校验未通过抛出的异常（如自定义 `BusinessException`）默认会记为 `status=1` 失败，与真正的系统异常混在一起。通过声明"业务异常"，可将其单独记为 `status=2` 业务失败，异常仍照常向上抛出，不影响全局异常处理。

**状态语义**：

| status | 含义 | 说明 |
|:--:|------|------|
| `0` | 成功 | 方法正常返回 |
| `1` | 失败 | 系统异常（未声明的异常） |
| `2` | 业务失败 | 声明的业务异常（校验失败等），errorMsg 保留 |

**两种声明方式（取并集，子类自动匹配）**：

```yaml
# 方式一：全局配置（适合 BusinessException 全局生效）
mok:
  operation-log:
    business-exceptions:
      - com.example.common.exception.BusinessException
```

```java
// 方式二：注解声明（适合单个接口差异化）
@OperationLog(title = "删除用户", businessExceptions = {BusinessException.class})
@DeleteMapping("/user/{id}")
public R delete(@PathVariable Long id) { ... }
```

> 类名写错（无法加载）时启动阶段打 WARN 并忽略该项，不影响其他配置和业务运行。

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

Starter 会采集 `operatorId`、`operatorName`、`operatorType` 和 `deptName`。默认实现无法获取的字段可能为 `UNKNOWN` 或 `null`；接入方可以通过自定义 `OperatorResolver` 提供完整信息。

`OperatorResolver.resolve()` 会一次性返回 `OperatorInfo`。需要查询用户数据库时建议覆盖该方法，避免四个 getter 各执行一次查询；旧的四个 getter 实现仍然兼容。

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

    public MyOperationLogConsumer(OperationLogService service,
                                  RabbitTemplate rabbitTemplate) {
        super(service, rabbitTemplate);
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
  │     零外部依赖                        去重后写入
  │
  └── rabbitmq(可选) ──→ RabbitMQ ──→ Consumer(retry+DLX+幂等)
        需引入 amqp                    完整保留所有 MQ 逻辑
  ▼
OperationLogService
  ├── OperationLogFileServiceImpl（文件 / JSON Lines，默认）
  ├── OperationLogMySqlServiceImpl（MySQL + MyBatis-Plus）
  └── OperationLogESServiceImpl（Elasticsearch）
```

---

## 项目结构

```
src/main/java/top/jiangmok/operationlog/
├── annotation/OperationLog.java              # @OperationLog 注解
├── aspect/OperationLogAspect.java            # AOP 切面
├── autoconfigure/                            # 自动配置（4 个）
│   ├── OperationLogAutoConfiguration.java    # 核心配置
│   ├── OperationLogFileAutoConfiguration.java # 文件存储（默认）
│   ├── OperationLogMySqlAutoConfiguration.java # MySQL 存储
│   └── OperationLogESAutoConfiguration.java   # ES 存储
├── config/OperationLogProperties.java        # 配置属性
├── constant/OperationLogMQConstant.java      # MQ 常量
├── consumer/OperationLogConsumer.java        # MQ 消费者（rabbitmq 策略）
├── desensitize/                              # 参数脱敏 SPI
├── entity/OperationLogEntity.java            # 实体
├── enums/BusinessType.java                   # 业务类型枚举
├── mapper/OperationLogMapper.java            # MyBatis-Plus Mapper
├── message/OperationLogMessage.java          # 消息体
├── model/OperationLogPageResult.java         # 标准分页结果
├── operator/                                 # 操作人解析器 SPI
│   └── OperatorInfo.java                     # 操作人快照
├── repository/OperationLogRepository.java    # ES Repository
├── sender/                                   # 异步发送器 SPI
│   └── impl/
│       ├── AsyncOperationLogSender.java      # @Async 默认实现
│       └── RabbitMQAsyncSender.java          # RabbitMQ 实现
├── service/
│   ├── OperationLogService.java              # 存储接口（SPI）
│   └── impl/
│       ├── OperationLogFileServiceImpl.java  # 文件存储（默认，纯 JDK）
│       ├── OperationLogMySqlServiceImpl.java # MySQL 存储
│       └── OperationLogESServiceImpl.java    # ES 存储
└── util/IdGenerator.java                     # ID 生成工具
```

---

## 依赖说明

| 依赖 | 必选 | 说明 |
|------|:--:|------|
| spring-boot-starter-aop | ✅ | AOP 切面 |
| spring-boot-starter-web | 接入方提供 | HTTP 请求采集需要；POM 中为 optional |
| jackson-databind | ✅ | JSON 序列化 |
| spring-boot-starter-amqp | ❌ | RabbitMQ 策略时需要 |
| mybatis-plus-spring-boot3-starter | ❌ | MySQL 存储时需要 |
| mysql-connector-j | ❌ | MySQL 存储时需要 |
| spring-boot-starter-data-elasticsearch | ❌ | ES 存储时需要 |
| sa-token-spring-boot3-starter | ❌ | 自动解析 Sa-Token 操作人时需要 |
| spring-boot-starter-security | ❌ | 自动解析 Security 操作人时需要 |

## 构建与发布

日常构建不会触发 GPG：

```bash
mvn clean verify
```

正式发布时显式启用 `release` Profile：

```bash
mvn -Prelease clean deploy
```

`release` Profile 会生成源码包、Javadoc、GPG 签名并启用 Central Publishing。Maven Central 已发布版本不能覆盖，发布前必须确认版本号和测试结果。

当前 29 项自动化测试覆盖配置开关、SPI、业务失败分类、文件分页、MySQL 异常传播和 RabbitMQ 重试流程。真实 MySQL、Elasticsearch、RabbitMQ 环境的端到端验证仍应在正式发布前执行。

## License

[Apache License 2.0](LICENSE)
