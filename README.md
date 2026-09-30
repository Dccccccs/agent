# Agent Gateway Demo

基于 Java 8、Spring Boot 2.7、MapStruct 和四层架构，实现统一查询入口、问题拦截、意图识别、Agent 动态路由、执行和异步监控。

## 调用链

```text
AgentGatewayController.query
  -> AgentGatewayService.query
  -> QuestionGuardHandler       @Order(10)
  -> IntentRecognitionHandler   @Order(20)
  -> AgentRouteHandler          @Order(30)
  -> AgentExecuteHandler        @Order(40)
  -> InvocationMonitoringSink（异步监控）
```

## 查询接口

```bash
curl -X POST http://localhost:8080/api/v1/agent/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "今天上海天气怎么样",
    "msgId": "msg-001",
    "sessionId": "session-001",
    "uid": "uid-001",
    "appId": "app-001",
    "rewriteQuestion": "上海天气",
    "slot": {
      "MutualFund": [{
        "start": 0,
        "end": 4,
        "realStart": 0,
        "realEnd": 4,
        "key": "示例基金",
        "matched": "示例基金",
        "matchedInfo": {
          "name": "示例基金",
          "origin_name": "示例基金",
          "code": "000001",
          "shortName": "示例",
          "name_abr": "SL",
          "stand_name": "示例基金",
          "fund_type": "混合型",
          "product_type": "公募基金"
        }
      }]
    },
    "extraParam": {"channel": "app"}
  }'
```

响应示例：

```json
{
  "workflowId": "weather-workflow-msg-001",
  "answer": "天气Agent已执行，实际项目中此处调用在线天气工作流",
  "question": "今天上海天气怎么样"
}
```

`query` 直接返回具体 Agent 的 Application Response，不对外暴露 `msgId`、`intentId`、`intentResult`、`agentId` 或 `data` 包装层。这些信息仍会完整写入内部监控事件。

## 四层职责

- `interfaces`：HTTP/SSE Controller、对外输出 DTO，以及接口协议装配器。
- `application`：`AixzChatRequest` 等输入模型、用例编排、Handler 处理链、最终 Agent Response 和对象装配器。
- `domain`：无框架依赖的不可变请求模型、Agent 契约、领域结果、端口和异常。
- `infrastructure`：意图识别/问题拦截适配器、Agent 实现、结果转换器、异步线程池和监控 Sink。

输入 DTO 使用 `@Data` 便于 Jackson 反序列化；领域实体统一在类上使用 `@Getter + @Builder`，不提供 setter，也不手写构造器。集合字段优先使用 `@Singular` 保持 Builder 构建后的只读语义；领域服务、端口、枚举和异常类不适用该实体规范。Jackson 注解不会进入领域层。

## 意图与 Agent 路由

```text
WEATHER_INTENT     -> weather-agent
ORDER_QUERY_INTENT -> order-agent
UNKNOWN_INTENT     -> fallback-agent
```

`AgentRegistry` 自动收集 `AgentExecutor`。新增 Agent 时提供 AgentExecutor、具体 AgentResult、AgentResultConverter，并配置 `intentId -> agentId` 映射。

Agent 根据 `AgentInfo.useOffline` 声明结果模式：

```text
useOffline = 1 -> OfflineAgentResult -> 读取 recordId
useOffline = 0 -> OnlineAgentResult  -> 读取 workflowId
```

网关将选中的值统一写入监控事件的 `agentResultId`。数据库 Sink 应将 `agentResultId` 映射到日志表需要保存的结果标识字段；`useOffline`、`recordId` 和 `workflowId` 也会保留，便于审计和排查。

## 异步监控

应用层不可变的 `InvocationMonitoringEvent` 会完整保存输入中的：

- `question`、`msgId`、`sessionId`、`uid`、`appId`；
- `rewriteQuestion`、完整 `slot` 和 `extraParam`；
- `intentId`、`intentResult`、`agentId`、Agent 记录 ID 和返回答案；
- `useOffline`、原始 `recordId`、原始 `workflowId` 和最终选中的 `agentResultId`；
- 请求/返回时间、意图请求/返回时间、意图耗时和总耗时；
- 成功状态、错误码和错误信息。

四个时间点格式为 `yyyy-MM-dd HH:mm:ss`，`intentRecognitionMillis` 和 `totalMillis` 单位为毫秒。`InvocationMonitoringService.publish` 接收监控事件而不是领域实体，并使用独立线程池异步调用所有 `InvocationMonitoringSink`。Demo 默认输出 JSON 日志；接数据库时由数据库 Sink 将事件转换成 DO/PO 后再调用 Mapper。

## 对象转换与 MapStruct

无状态、字段型的对象转换优先使用 MapStruct 接口：

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
```

命名约定和主要实现：

- application 层的对象转换统一命名为 `*Assembler`；
- infrastructure 层的对象转换统一命名为 `*Converter`；
- `interfaces.assembler.AgentApiAssembler`：输入 DTO 转领域请求；
- `application.assembler.response.*AgentResponseAssembler`：不同 Domain AgentResult 转最终 Application Response；
- `application.assembler.InvocationMonitoringEventAssembler`：请求与上下文转监控事件快照；
- `interfaces.filteredstream.assembler.FilteredStreamDtoAssembler`：流式领域结果和应用事件转输出 DTO；
- `infrastructure.converter.*AgentResultConverter`：Agent 原始结果转具体结果。

涉及 JSON 语法解析、帧类型分派或运行时定制器的转换不强行做成纯 MapStruct 映射。例如 `FilteredStreamFrameAssembler` 负责对外协议装配，内部再委托 `FilteredStreamDtoAssembler` 完成静态字段映射；`UpstreamStreamFrameConverter` 负责解析外部厂商 JSON 协议。

承载业务状态、传输会话、回调处理或映射数据的类型均使用独立顶层类，不在 Controller、Service、领域对象中声明内部类；单方法回调优先使用 Lambda，多方法回调使用独立实现类。

## 运行

```bash
mvn test
mvn spring-boot:run
```

## 流式 Agent

`query` 接口会在完成意图识别和 `intentId -> agentId` 路由后，根据 Agent 实现类型自动选择响应方式：

- 普通 Agent 实现 `AgentExecutor`，返回 `application.response.AgentResponse`，HTTP 响应为 `application/json`。
- 流式 Agent 实现 `StreamingAgentExecutor`，返回 `AgentStream`，HTTP 响应为 `text/event-stream`。

Demo 中的问题包含 `流式` 或 `stream` 时，会路由到 `stream-chat-agent`。调用示例：

```bash
curl -N -X POST http://localhost:8080/api/v1/agent/query \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"question":"请流式回答","msgId":"msg-stream-001","uid":"uid-001"}'
```

SSE 事件包括：

- `message`：增量内容，包含 `sequence` 和 `content`。
- `complete`：流式 Agent 的最终 Application Response。
- `error`：统一异常码和错误信息。

流式监控在流真正结束时发布，而不是在 `query` 方法返回时发布。除原有字段外，还记录 `streaming`、`streamEndReason`、`chunkCount` 和 `firstChunkMillis`；完成、异常、客户端取消只会记录一次终态。

## 带逐帧检测的独立流式接口

`POST /api/v1/model-stream/filtered-query` 是独立的流式用例。它复用 `AixzChatRequest` 作为接口入参，但不依赖 Agent 网关的领域请求、流接口、结果模型、API Assembler、问题拦截、意图识别、Agent 路由或执行链。原有 `/api/v1/agent/query` 的协议和行为不受影响。

整个工程只保留四个顶层业务包；不同场景放在各层内部的子包中，例如 `application.filteredstream`、`domain.filteredstream`，不再使用 `feature.filteredstream.application` 这种按场景重复四层的结构：

```text
interfaces      HTTP/SSE 协议、Controller、返回 DTO 和帧映射
    ↓
application     请求校验编排、上游调用编排、应用事件和端口
    ↓
domain          跨帧状态机、敏感词/卡片/链接规则及处理结果
    ↑
infrastructure  WebClient、上游协议解码、配置校验和数据目录实现
```

`FilteredStreamService` 只依赖 `ExternalModelStreamPort` 和 `FilteredStreamRequestValidator`，不知道 WebClient、SSE 发送细节和接口 DTO。`ExternalModelSseClient`、默认校验器及演示数据目录位于 infrastructure，并实现内层声明的端口；Controller 只把应用层的拒绝结果或 `FilteredStreamEvent` 转换成 HTTP/SSE 响应。

外部 API 帧的模拟与生产处理代码定位：

1. `FilteredModelStreamIntegrationTest.stubExternalModelApi`：Mock `ExternalModelStreamPort`，依次返回一个统一 Metadata 帧和多个统一增量正文帧，用于验证完整接口行为。
2. `UpstreamStreamFrameConverterTest`：直接构造外部厂商的原始 JSON，包含 `METADATA`、`MIDDLE`、`END`、`ERROR`，这是查看和修改“模拟外部原始帧结构”的主要位置。
3. `ExternalModelSseClient.responseFrames`：生产环境中使用 WebClient POST 调用外部 API；`decodeBody` 从 HTTP 响应逐条读取 SSE data。
4. `UpstreamStreamFrameConverter.convert`：把每条原始 data 字符串转换为 `UpstreamStreamFrame`。外部协议字段只在这里解析。
5. `FilteredStreamService.createEventStream/processFrame`：顺序消费统一帧。Metadata 按目标 JSON 对象结构输出并注册业务数据；正文交给 `StreamingContentProcessor`；异常统一生成纯 `FALLBACK` 通知帧。

因此，如果外部 API 将来调整字段名或帧结构，优先修改 `UpstreamStreamFrameConverter` 及对应测试；如果只是调整敏感词、卡片、URL 的跨帧规则，则修改 `StreamingContentProcessor`，不需要改 WebClient 调用代码。

如果最终首帧需要在外部元数据基础上增加、删除或计算字段，请新增一个 `MetadataOutputCustomizer` Bean，不要修改上游解码器。例如：

```java
@Component
@Order(100)
public class FundMetadataOutputCustomizer
        implements MetadataOutputCustomizer {
    @Override
    public void customize(ObjectNode body) {
        // 删除不需要向客户端暴露的内部字段。
        body.remove("internalPrompt");

        // 增加固定字段。
        body.put("channel", "fund-assistant");

        // 根据原有字段计算新字段。
        body.put("fundCount", body.path("linkMapping").size());
    }
}
```

`FilteredStreamFrameAssembler` 会复制 Metadata body 后按 `@Order` 顺序调用所有定制器，因此定制器仍可读取上游 `linkMapping`、`cardMapping` 计算其他输出字段。定制完成后系统会统一删除顶层的 `linkMapping`、`cardMapping`、`suggestedQuery`，写入配置生成的 `fallbackAnswer`，并固定 `recordId` 和外层 `returnType`。

默认上游地址配置在 `application.yml`：

```yaml
filtered-stream:
  workflow-id: filtered-model-stream-workflow-v1
  fallback-answer:
    content: 抱歉，当前服务暂时不可用，请稍后再试。
    link-mapping: []
    card-mapping: []
  upstream:
    url: http://localhost:18080/api/v1/chat/stream
  validation:
    blocked-keywords: []
    refusal-message: 请求内容未通过校验
```

部署时可通过配置中心或环境变量 `FILTERED_STREAM_UPSTREAM_URL` 覆盖。客户端会把收到的 `AixzChatRequest` 原样作为 JSON 请求体 POST 到该地址，并声明接收 `text/event-stream`。HTTP 请求头由接口层转换成与 Web 框架无关的 `FilteredStreamRequestContext`，随业务请求显式传到应用层和基础设施层；不使用在 Reactor 异步线程中容易丢失的 `ThreadLocal`。

`FilteredStreamService` 会先调用 `FilteredStreamRequestValidator` 进行业务校验。校验通过才调用外部模型；校验拒绝时不会发起上游请求，因此和“外部 API 在首帧前失败”一样先输出包含请求 `msgId` 与兜底答案的基础 METADATA，再输出纯 FALLBACK 通知和 DONE：

```json
{"body":{"fallbackAnswer":[{"type":"text","content":"抱歉，当前服务暂时不可用，请稍后再试。","linkMapping":[],"cardMapping":[]}],"recordId":"msg-filter-001"},"returnType":"METADATA"}
{"returnType":"FALLBACK"}
{"returnType":"DONE"}
```

默认实现按照 `blocked-keywords` 校验问题文本；生产环境可以提供自己的 `FilteredStreamRequestValidator` Bean，接入机审、数据库规则或远程校验服务，届时默认实现会自动退出。

上游响应支持纯文本 SSE data、`choices[0].delta.content` 格式，以及本文档中的 `METADATA / DATA / ERROR / DONE` JSON 帧；解析出的增量 `content` 再进入跨帧检测状态机。HTTP 错误、上游 `ERROR` 帧、连接异常和内容规则异常不会向客户端暴露真实错误，统一转换为纯 FALLBACK 通知帧。

接口按顺序返回：

```text
METADATA -> DATA（零到多帧） -> 推荐问 METADATA（满足条件时） -> DONE
首帧前失败 -> 基础 METADATA(recordId + fallbackAnswer) -> FALLBACK -> DONE
METADATA(content.status=FALLBACK) -> FALLBACK -> DONE
METADATA/DATA -> FALLBACK（流中发生错误） -> DONE
```

第一帧元数据通常来自外部 API。对于外层 `status: METADATA` 帧，`content` 可以是已经解析好的 JSON 对象，也兼容 JSON 字符串；接口将它转换成真正的 JSON 对象 `{"body": {...}, "returnType": "METADATA"}` 后再交给 SSE 消息转换器。外部包装层的 `label`、外层 `status=METADATA`、`content` 不向下游透传；`recordId` 始终覆盖为本次 `AixzChatRequest.msgId`。上游顶层的 `linkMapping`、`cardMapping`、`suggestedQuery` 均不在首帧输出，但首帧始终新增 `fallbackAnswer` 数组；数组中的答案块固定包含 `type`、`content`、`linkMapping`、`cardMapping`。上游三个字段仍保存在强类型 `StreamMetadata` 或原始处理上下文中：前两个用于后续 URL、卡片校验，推荐问用于结束阶段合并输出。状态缺失、枚举非法或首帧前发生异常时，接口补发包含请求 `msgId` 和 `fallbackAnswer` 的基础 METADATA 后再输出兜底。

处理器支持以下跨帧规则：

- 敏感词使用 `Map<String, String>` 做最长匹配；半截敏感词会阻塞当前整帧，匹配成功后合并输出替换结果，匹配失败则恢复原文。
- `<m-card id='card_xxx' />` 会跨帧缓存至 `/>`，再将标签中的 `card_xxx` 与上游 Metadata 中暂存的 `cardMapping[].cardPlaceHolder` 精确匹配。匹配成功后，使用该对象构建标准卡片标签及内容帧 `cardMapping`；首帧不会暴露原始 `cardMapping`。
- URL 只支持 `<url id="000503" text="基金名称" />` 这一种格式，并会跨帧缓存至 `/>`。其中 `id` 必须匹配上游 Metadata 中暂存的 `linkMapping[].linkId`；命中后输出标准 URL 标签及内容帧 `linkMapping`；首帧不会暴露原始 `linkMapping`。
- 流正常结束时，普通敏感词前缀和单独的 `<` 按原文输出；未闭合的 `<m-card` 或已确认的 `<url` 标签触发兜底替换帧。

兜底回答通过 `filtered-stream.fallback-answer` 配置，并由 `FallbackMessage` 统一承载 `content`、`linkMapping`、`cardMapping`。该对象会组装成标准答案块并始终以数组形式出现在首帧 `body.fallbackAnswer` 中。真正进入兜底分支时，服务端不再重复输出答案，只发送：

```json
{"returnType":"FALLBACK"}
```

无论错误发生在首帧前还是流式处理中，FALLBACK 帧都不包含 `body`、`answer`、`operate` 或错误详情，由客户端展示首帧中预先取得的 `fallbackAnswer`。其后固定紧跟一个纯 DONE 帧：

```json
{"returnType":"DONE"}
```

流处理器会记录正文中第一个完整、合法且存在于 `body.linkMapping` 的基金 URL。正常结束时，应用服务先保留上游 `suggestedQuery` 的原有顺序，再把根据首个基金 URL 构建的“深度解读”推荐问追加到列表末尾，并在 `DONE` 前通过一个 METADATA 帧统一输出：

```json
{
  "body": {
    "suggestedQuery": [
      {"query": "上游推荐问", "intent": null},
      {"query": "深度解读华夏成长", "intent": "null"}
    ]
  },
  "returnType": "METADATA"
}
```

没有基金 URL 但上游提供了推荐问时，仍会输出上游推荐问；两类推荐问都不存在时才省略推荐问帧。`DONE` 帧始终保持 `{"returnType":"DONE"}`。

Demo 目录现在只提供敏感词替换表。基金使用首帧 `linkMapping`，卡片使用首帧 `cardMapping`，二者都按请求隔离，不会污染其他流。

调用示例：

```bash
curl -N -X POST http://localhost:8080/api/v1/model-stream/filtered-query \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -H "intent-id: FUND_RECOMMEND_INTENT" \
  -d '{"question":"stream 极端高","msgId":"msg-filter-001","uid":"uid-001"}'
```

`intent-id` 是必填请求头，同时兼容历史写法 `intentId`。接口层会把请求头统一转换为小写键并生成只读快照，应用层可通过 `FilteredStreamRequestContext.getHeader("header-name")` 读取新增参数。`ExternalModelSseClient` 目前只将白名单中的 `intent-id` 透传给外部模型 API，不会写入或修改 `AixzChatRequest`，也不会转发 Cookie、Host 等无关请求头。请求头缺失、值为空或其他输入校验失败时，不调用外部 API，按现有协议返回包含 `recordId` 与 `fallbackAnswer` 的基础 METADATA、纯 FALLBACK 通知帧和 DONE。

接口建立 SSE 响应时会立即返回固定响应头：

```text
workFlowId: filtered-model-stream-workflow-v1
```

该值不依赖外部模型首帧，通过 `filtered-stream.workflow-id` 配置；部署时可使用
环境变量 `FILTERED_STREAM_WORKFLOW_ID` 覆盖。请求体解析失败等发生在 Controller
执行前的异常响应也会携带该响应头。
