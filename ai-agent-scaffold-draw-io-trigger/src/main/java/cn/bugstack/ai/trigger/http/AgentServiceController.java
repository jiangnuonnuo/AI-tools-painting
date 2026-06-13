package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.IAgentService;
import cn.bugstack.ai.api.dto.*;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.service.IChatService;
import cn.bugstack.ai.trigger.http.support.ChatResponseParser;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import cn.bugstack.ai.domain.agent.service.chat.CustomApiConfigManager;
import cn.bugstack.ai.trigger.http.support.ChatStreamJsonAccumulator;
import cn.bugstack.ai.trigger.http.support.ChatStreamResponseFormatter;
import org.apache.commons.lang3.StringUtils;

/**
 * description: AI Agent 服务 HTTP 控制器。
 * v2 流式协议：每行 NDJSON 包含 seq/phase/author/event/renderable/final/chunk 字段。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/1/20 08:23
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class AgentServiceController implements IAgentService {

    @Resource
    private IChatService chatService;

    @RequestMapping(value = "query_ai_agent_config_list", method = RequestMethod.GET)
    @Override
    public Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList() {
        try {
            log.info("查询智能体配置列表");

            List<AiAgentConfigTableVO.Agent> agentConfigs = chatService.queryAiAgentConfigList();

            List<AiAgentConfigResponseDTO> responseDTOS = agentConfigs.stream().map(agentConfig -> {
                AiAgentConfigResponseDTO responseDTO = new AiAgentConfigResponseDTO();
                responseDTO.setAgentId(agentConfig.getAgentId());
                responseDTO.setAgentName(agentConfig.getAgentName());
                responseDTO.setAgentDesc(agentConfig.getAgentDesc());
                return responseDTO;
            }).collect(Collectors.toList());

            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOS)
                    .build();

        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("查询智能体配置列表失败", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "create_session", method = RequestMethod.POST)
    @Override
    public Response<CreateSessionResponseDTO> createSession(@RequestBody CreateSessionRequestDTO requestDTO) {
        try {
            log.info("创建会话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());

            CreateSessionResponseDTO responseDTO = new CreateSessionResponseDTO();
            responseDTO.setSessionId(sessionId);

            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("创建会话失败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "create_session", method = RequestMethod.GET)
    public Response<CreateSessionResponseDTO> createSession(@RequestParam("agentId") String agentId, @RequestParam("userId") String userId) {
        CreateSessionRequestDTO requestDTO = new CreateSessionRequestDTO();
        requestDTO.setAgentId(agentId);
        requestDTO.setUserId(userId);
        return createSession(requestDTO);
    }

    @RequestMapping(value = "chat", method = RequestMethod.POST)
    @Override
    public Response<ChatResponseDTO> chat(@RequestBody ChatRequestDTO requestDTO) {
        try {
            log.info("智能体对话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = requestDTO.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
            }

            CustomApiConfigManager.CustomApiConfig config = CustomApiConfigManager.CustomApiConfig.builder()
                    .baseUrl(requestDTO.getCustomBaseUrl())
                    .apiKey(requestDTO.getCustomApiKey())
                    .completionsPath(requestDTO.getCustomCompletionsPath())
                    .model(requestDTO.getCustomModel())
                    .customModelSelected(StringUtils.isNotBlank(requestDTO.getCustomModel()))
                    .build();
            CustomApiConfigManager.setConfig(sessionId, config);

            List<String> messages = chatService.handleMessage(requestDTO.getAgentId(), requestDTO.getUserId(), sessionId, requestDTO.getMessage());

            ChatResponseDTO responseDTO = ChatResponseParser.parse(messages);

            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("智能体对话异常", e);
            return Response.<ChatResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("智能体对话失败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "chat_stream", method = RequestMethod.POST)
    @Override
    public ResponseBodyEmitter chatStream(@RequestBody ChatRequestDTO requestDTO) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(5 * 60 * 1000L) {
            @Override
            protected void extendResponse(org.springframework.http.server.ServerHttpResponse outputMessage) {
                outputMessage.getHeaders().set("Content-Type", "application/x-ndjson");
            }
        };
        try {
            log.info("流式对话 agentId:{} userId:{} sessionId:{} message:{}", requestDTO.getAgentId(), requestDTO.getUserId(), requestDTO.getSessionId(), requestDTO.getMessage());

            String sessionId = requestDTO.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
            }

            final String finalSessionId = sessionId;

            CustomApiConfigManager.CustomApiConfig config = CustomApiConfigManager.CustomApiConfig.builder()
                    .baseUrl(requestDTO.getCustomBaseUrl())
                    .apiKey(requestDTO.getCustomApiKey())
                    .completionsPath(requestDTO.getCustomCompletionsPath())
                    .model(requestDTO.getCustomModel())
                    .customModelSelected(StringUtils.isNotBlank(requestDTO.getCustomModel()))
                    .build();
            CustomApiConfigManager.setConfig(finalSessionId, config);

            final java.util.concurrent.ConcurrentHashMap<String, ChatStreamJsonAccumulator> authorAccumulators = new java.util.concurrent.ConcurrentHashMap<>();
            final AtomicInteger seq = new AtomicInteger(0);

            io.reactivex.rxjava3.disposables.Disposable disposable = chatService.handleMessageStream(requestDTO.getAgentId(), requestDTO.getUserId(), finalSessionId, requestDTO.getMessage())
                    .subscribe(
                            event -> {
                                try {
                                    String author = StringUtils.defaultIfBlank(event.author(), "unknown");
                                    String phase;
                                    switch (author) {
                                        case "agent_analyst":
                                        case "agent_ppt_analyst":
                                            phase = "analyzing";
                                            break;
                                        case "agent_drawer":
                                            phase = "drawing";
                                            break;
                                        case "agent_ppt_generator":
                                            phase = "generating";
                                            break;
                                        case "agent_reviewer":
                                        case "agent_ppt_reviewer":
                                            phase = "reviewing";
                                            break;
                                        default:
                                            phase = "thinking";
                                    }

                                    if (!event.functionCalls().isEmpty() || !event.functionResponses().isEmpty()) {
                                        return;
                                    }

                                    String content = event.stringifyContent();
                                    if (content == null || content.isEmpty()) {
                                        return;
                                    }

                                    ChatStreamJsonAccumulator accumulator = authorAccumulators.computeIfAbsent(author, k -> new ChatStreamJsonAccumulator());
                                    List<ChatStreamJsonAccumulator.Segment> segments = accumulator.append(content);
                                    for (ChatStreamJsonAccumulator.Segment segment : segments) {
                                        processAndSendSegment(emitter, phase, author, seq, segment);
                                    }
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            error -> {
                                if (error instanceof IllegalStateException && error.getMessage() != null && error.getMessage().contains("ResponseBodyEmitter has already completed")) {
                                    log.warn("流式对话已结束(客户端断开或主动完成)");
                                    return;
                                }
                                if (error instanceof java.io.IOException || (error.getMessage() != null && error.getMessage().contains("Broken pipe"))) {
                                    log.warn("流式对话连接断开: {}", error.getMessage());
                                    return;
                                }
                                if (error.getCause() instanceof IllegalStateException && error.getCause().getMessage() != null && error.getCause().getMessage().contains("ResponseBodyEmitter has already completed")) {
                                    log.warn("流式对话已结束(客户端断开或主动完成)");
                                    return;
                                }
                                if (error.getCause() instanceof java.io.IOException) {
                                    log.warn("流式对话连接断开: {}", error.getCause().getMessage());
                                    return;
                                }
                                log.error("流式对话异常", error);
                                try {
                                    com.alibaba.fastjson.JSONObject errMsg = new com.alibaba.fastjson.JSONObject();
                                    errMsg.put("seq", seq.getAndIncrement());
                                    errMsg.put("phase", "error");
                                    errMsg.put("author", "system");
                                    errMsg.put("event", "error");
                                    errMsg.put("renderable", false);
                                    errMsg.put("final", true);
                                    com.alibaba.fastjson.JSONObject chunk = new com.alibaba.fastjson.JSONObject();
                                    chunk.put("type", "error");
                                    chunk.put("content", "对话异常，请重试");
                                    errMsg.put("chunk", chunk);
                                    emitter.send(errMsg.toJSONString() + "\n");
                                } catch (Exception ignored) {}
                                emitter.completeWithError(error);
                            },
                            () -> {
                                for (java.util.Map.Entry<String, ChatStreamJsonAccumulator> entry : authorAccumulators.entrySet()) {
                                    String author = entry.getKey();
                                    ChatStreamJsonAccumulator accumulator = entry.getValue();
                                    List<ChatStreamJsonAccumulator.Segment> segments = accumulator.flush();
                                    for (ChatStreamJsonAccumulator.Segment segment : segments) {
                                        try {
                                            processAndSendSegment(emitter, "done", author, seq, segment);
                                        } catch (Exception ignored) {
                                        }
                                    }
                                }
                                try {
                                    com.alibaba.fastjson.JSONObject doneMsg = new com.alibaba.fastjson.JSONObject();
                                    doneMsg.put("seq", seq.getAndIncrement());
                                    doneMsg.put("phase", "done");
                                    doneMsg.put("author", "system");
                                    doneMsg.put("event", "done");
                                    doneMsg.put("renderable", false);
                                    doneMsg.put("final", true);
                                    com.alibaba.fastjson.JSONObject chunk = new com.alibaba.fastjson.JSONObject();
                                    chunk.put("type", "done");
                                    doneMsg.put("chunk", chunk);
                                    emitter.send(doneMsg.toJSONString() + "\n");
                                } catch (Exception ignored) {}
                                emitter.complete();
                            }
                    );

            emitter.onCompletion(() -> {
                log.info("流式对话 emitter.onCompletion sessionId:{}", finalSessionId);
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
            });
            emitter.onTimeout(() -> {
                log.info("流式对话 emitter.onTimeout sessionId:{}", finalSessionId);
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
            });
            emitter.onError(e -> {
                log.info("流式对话 emitter.onError sessionId:{}", finalSessionId);
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
            });
        } catch (Exception e) {
            log.error("流式对话失败", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * description: 处理单行模型输出，转换为统一流式响应协议后发送到前端。
     *
     * @param emitter input HTTP 流式响应发送器
     * @param phase   input 当前智能体执行阶段
     * @param author  input 当前输出的 agent 名称
     * @param seq     input 流式序号计数器
     * @param line    input 模型输出的单行文本
     * @throws Exception output 发送失败时抛出异常
     */
    private void processAndSendLine(ResponseBodyEmitter emitter, String phase, String author, AtomicInteger seq, String line) throws Exception {
        ChatStreamResponseFormatter.FormatResult formatResult = ChatStreamResponseFormatter.format(phase, author, line);
        emitter.send(injectSeq(formatResult.toNdjsonLine(), seq.getAndIncrement()));

        if (formatResult.isDrawPauseRequired()) {
            Thread.sleep(250);
        }
    }

    /**
     * description: 处理聚合后的流式片段，转换为统一响应协议后发送到前端。
     *
     * @param emitter input HTTP 流式响应发送器
     * @param phase   input 当前智能体执行阶段
     * @param author  input 当前输出的 agent 名称
     * @param seq     input 流式序号计数器
     * @param segment input 聚合后的完整 JSON 或普通文本片段
     * @throws Exception output 发送失败时抛出异常
     */
    private void processAndSendSegment(ResponseBodyEmitter emitter, String phase, String author, AtomicInteger seq,
                                       ChatStreamJsonAccumulator.Segment segment) throws Exception {
        processAndSendLine(emitter, phase, author, seq, segment.getContent());
    }

    /**
     * description: 将 NDJSON 行的第一个 "{" 替换为 "{"seq":N,"，注入序号。
     *
     * @param ndjsonLine input Formatter 返回的 NDJSON 行
     * @param seq        input 序号
     * @return output 注入序号后的 NDJSON 行
     */
    private String injectSeq(String ndjsonLine, int seq) {
        int firstBrace = ndjsonLine.indexOf('{');
        if (firstBrace < 0) {
            return ndjsonLine;
        }
        return ndjsonLine.substring(0, firstBrace + 1) + "\"seq\":" + seq + "," + ndjsonLine.substring(firstBrace + 1);
    }

}
