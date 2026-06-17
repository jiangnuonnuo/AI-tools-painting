package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.dto.ChatResponseDTO;
import cn.bugstack.ai.api.dto.PromptRequestDTO;
import cn.bugstack.ai.api.dto.PromptResponseDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.service.IChatService;
import cn.bugstack.ai.domain.agent.service.chat.CustomApiConfigManager;
import cn.bugstack.ai.trigger.http.support.ChatResponseParser;
import cn.bugstack.ai.trigger.http.support.ChatStreamJsonAccumulator;
import cn.bugstack.ai.trigger.http.support.ChatStreamResponseFormatter;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.AppException;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author xerina
 * @description Prompt 工程智能体 HTTP 控制器，提供 Prompt 生成、整体改写、局部改写的专属入口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/prompt/")
@CrossOrigin(origins = "*")
public class PromptAgentController {

    private static final String DEFAULT_PROMPT_AGENT_ID = "300002";
    private static final String MODE_GENERATE = "generate";
    private static final String MODE_REWRITE = "rewrite";
    private static final String MODE_PARTIAL_REWRITE = "partial_rewrite";

    @Resource
    private IChatService chatService;

    /**
     * @description 生成或改写 Prompt，返回完整 Prompt 文本。
     *
     * @param requestDTO input Prompt 请求参数
     * @return output Prompt 生成或改写结果
     */
    @RequestMapping(value = "generate_prompt", method = RequestMethod.POST)
    public Response<PromptResponseDTO> generatePrompt(@RequestBody PromptRequestDTO requestDTO) {
        try {
            Response<PromptResponseDTO> validationResponse = validateRequest(requestDTO);
            if (null != validationResponse) {
                return validationResponse;
            }

            String agentId = resolveAgentId(requestDTO);
            String sessionId = resolveSessionId(requestDTO, agentId);
            setCustomApiConfig(sessionId, requestDTO);

            String message = buildPromptMessage(requestDTO);
            List<String> messages = chatService.handleMessage(agentId, requestDTO.getUserId(), sessionId, message);
            ChatResponseDTO chatResponseDTO = ChatResponseParser.parse(messages);

            PromptResponseDTO responseDTO = new PromptResponseDTO();
            responseDTO.setSessionId(sessionId);
            responseDTO.setType(chatResponseDTO.getType());
            responseDTO.setContent(chatResponseDTO.getContent());
            responseDTO.setMetadata(chatResponseDTO.getMetadata());

            return Response.<PromptResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("Prompt 工程智能体处理异常", e);
            return failResponse(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("Prompt 工程智能体处理失败", e);
            return failResponse(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
        }
    }

    /**
     * @description 流式生成或改写 Prompt，输出 NDJSON 流式事件。
     *
     * @param requestDTO input Prompt 请求参数
     * @return output HTTP 流式响应发送器
     */
    @RequestMapping(value = "generate_prompt_stream", method = RequestMethod.POST)
    public ResponseBodyEmitter generatePromptStream(@RequestBody PromptRequestDTO requestDTO) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(5 * 60 * 1000L) {
            @Override
            protected void extendResponse(org.springframework.http.server.ServerHttpResponse outputMessage) {
                outputMessage.getHeaders().set("Content-Type", "application/x-ndjson");
            }
        };

        try {
            Response<PromptResponseDTO> validationResponse = validateRequest(requestDTO);
            if (null != validationResponse) {
                sendErrorAndComplete(emitter, validationResponse.getInfo());
                return emitter;
            }

            String agentId = resolveAgentId(requestDTO);
            String sessionId = resolveSessionId(requestDTO, agentId);
            setCustomApiConfig(sessionId, requestDTO);

            String message = buildPromptMessage(requestDTO);
            AtomicInteger seq = new AtomicInteger(0);
            ConcurrentHashMap<String, ChatStreamJsonAccumulator> authorAccumulators = new ConcurrentHashMap<>();

            io.reactivex.rxjava3.disposables.Disposable disposable = chatService.handleMessageStream(agentId, requestDTO.getUserId(), sessionId, message)
                    .subscribe(
                            event -> processStreamEvent(emitter, seq, authorAccumulators, event),
                            error -> completeStreamWithError(emitter, seq, error),
                            () -> completeStream(emitter, seq, authorAccumulators)
                    );

            emitter.onCompletion(() -> disposeStream(disposable));
            emitter.onTimeout(() -> disposeStream(disposable));
            emitter.onError(e -> disposeStream(disposable));
        } catch (Exception e) {
            log.error("Prompt 工程智能体流式处理失败", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * @description 校验 Prompt 请求参数。
     *
     * @param requestDTO input Prompt 请求参数
     * @return output 校验失败响应，校验通过时返回 null
     */
    private Response<PromptResponseDTO> validateRequest(PromptRequestDTO requestDTO) {
        if (null == requestDTO) {
            return failResponse(ResponseCode.ILLEGAL_PARAMETER.getCode(), "请求参数不能为空");
        }

        if (StringUtils.isBlank(requestDTO.getUserId())) {
            return failResponse(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId 不能为空");
        }

        String mode = StringUtils.defaultIfBlank(requestDTO.getMode(), MODE_GENERATE);
        if (!MODE_GENERATE.equals(mode) && !MODE_REWRITE.equals(mode) && !MODE_PARTIAL_REWRITE.equals(mode)) {
            return failResponse(ResponseCode.ILLEGAL_PARAMETER.getCode(), "mode 只允许 generate、rewrite、partial_rewrite");
        }

        if (MODE_GENERATE.equals(mode) && StringUtils.isBlank(requestDTO.getGoal())) {
            return failResponse(ResponseCode.ILLEGAL_PARAMETER.getCode(), "generate 模式 goal 不能为空");
        }

        if (MODE_REWRITE.equals(mode)) {
            if (StringUtils.isBlank(requestDTO.getCurrentPrompt()) || StringUtils.isBlank(requestDTO.getEditInstruction())) {
                return failResponse(ResponseCode.ILLEGAL_PARAMETER.getCode(), "rewrite 模式 currentPrompt 和 editInstruction 不能为空");
            }
        }

        if (MODE_PARTIAL_REWRITE.equals(mode)) {
            if (StringUtils.isBlank(requestDTO.getCurrentPrompt()) || StringUtils.isBlank(requestDTO.getSelectedPromptText()) || StringUtils.isBlank(requestDTO.getEditInstruction())) {
                return failResponse(ResponseCode.ILLEGAL_PARAMETER.getCode(), "partial_rewrite 模式 currentPrompt、selectedPromptText 和 editInstruction 不能为空");
            }
        }

        return null;
    }

    /**
     * @description 解析智能体 ID，未传入时使用 Prompt 工程智能体默认 ID。
     *
     * @param requestDTO input Prompt 请求参数
     * @return output 智能体 ID
     */
    private String resolveAgentId(PromptRequestDTO requestDTO) {
        return StringUtils.defaultIfBlank(requestDTO.getAgentId(), DEFAULT_PROMPT_AGENT_ID);
    }

    /**
     * @description 获取会话 ID，未传入时复用现有对话服务创建会话。
     *
     * @param requestDTO input Prompt 请求参数
     * @param agentId    input 智能体 ID
     * @return output 会话 ID
     */
    private String resolveSessionId(PromptRequestDTO requestDTO, String agentId) {
        if (StringUtils.isNotBlank(requestDTO.getSessionId())) {
            return requestDTO.getSessionId();
        }

        return chatService.createSession(agentId, requestDTO.getUserId());
    }

    /**
     * @description 设置自定义模型 API 配置，保持与现有对话接口一致。
     *
     * @param sessionId  input 会话 ID
     * @param requestDTO input Prompt 请求参数
     */
    private void setCustomApiConfig(String sessionId, PromptRequestDTO requestDTO) {
        CustomApiConfigManager.CustomApiConfig config = CustomApiConfigManager.CustomApiConfig.builder()
                .baseUrl(requestDTO.getCustomBaseUrl())
                .apiKey(requestDTO.getCustomApiKey())
                .completionsPath(requestDTO.getCustomCompletionsPath())
                .model(requestDTO.getCustomModel())
                .customModelSelected(StringUtils.isNotBlank(requestDTO.getCustomModel()))
                .build();
        CustomApiConfigManager.setConfig(sessionId, config);
    }

    /**
     * @description 构建发送给 Prompt 工程智能体的结构化输入文本。
     *
     * @param requestDTO input Prompt 请求参数
     * @return output 结构化 JSON 文本
     */
    private String buildPromptMessage(PromptRequestDTO requestDTO) {
        JSONObject message = new JSONObject();
        message.put("mode", StringUtils.defaultIfBlank(requestDTO.getMode(), MODE_GENERATE));
        message.put("taskType", StringUtils.trimToEmpty(requestDTO.getTaskType()));
        message.put("goal", StringUtils.trimToEmpty(requestDTO.getGoal()));
        message.put("currentPrompt", StringUtils.trimToEmpty(requestDTO.getCurrentPrompt()));
        message.put("selectedPromptText", StringUtils.trimToEmpty(requestDTO.getSelectedPromptText()));
        message.put("editInstruction", StringUtils.trimToEmpty(requestDTO.getEditInstruction()));
        message.put("constraints", StringUtils.trimToEmpty(requestDTO.getConstraints()));
        message.put("outputFormat", StringUtils.trimToEmpty(requestDTO.getOutputFormat()));
        return message.toJSONString();
    }

    /**
     * @description 处理单个 ADK 流式事件并发送标准 NDJSON。
     *
     * @param emitter            input HTTP 流式响应发送器
     * @param seq                input/output 流式序号计数器
     * @param authorAccumulators input/output 按作者聚合的 JSON 片段缓存
     * @param event              input ADK 流式事件
     */
    private void processStreamEvent(ResponseBodyEmitter emitter, AtomicInteger seq,
                                    ConcurrentHashMap<String, ChatStreamJsonAccumulator> authorAccumulators,
                                    com.google.adk.events.Event event) {
        try {
            if (!event.functionCalls().isEmpty() || !event.functionResponses().isEmpty()) {
                return;
            }

            String content = event.stringifyContent();
            if (StringUtils.isBlank(content)) {
                return;
            }

            String author = StringUtils.defaultIfBlank(event.author(), "unknown");
            String phase = resolvePromptPhase(author);
            ChatStreamJsonAccumulator accumulator = authorAccumulators.computeIfAbsent(author, key -> new ChatStreamJsonAccumulator());
            List<ChatStreamJsonAccumulator.Segment> segments = accumulator.append(content);
            for (ChatStreamJsonAccumulator.Segment segment : segments) {
                sendStreamSegment(emitter, phase, author, seq, segment.getContent());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @description 将聚合后的流式片段格式化并发送给前端。
     *
     * @param emitter input HTTP 流式响应发送器
     * @param phase   input 当前阶段
     * @param author  input 当前作者
     * @param seq     input/output 流式序号计数器
     * @param content input 完整 JSON 或文本片段
     * @throws Exception output 发送失败时抛出异常
     */
    private void sendStreamSegment(ResponseBodyEmitter emitter, String phase, String author, AtomicInteger seq, String content) throws Exception {
        ChatStreamResponseFormatter.FormatResult formatResult = ChatStreamResponseFormatter.format(phase, author, content);
        emitter.send(injectSeq(formatResult.toNdjsonLine(), seq.getAndIncrement()));
    }

    /**
     * @description 流式处理完成时刷新剩余片段并发送完成事件。
     *
     * @param emitter            input HTTP 流式响应发送器
     * @param seq                input/output 流式序号计数器
     * @param authorAccumulators input/output 按作者聚合的 JSON 片段缓存
     */
    private void completeStream(ResponseBodyEmitter emitter, AtomicInteger seq,
                                ConcurrentHashMap<String, ChatStreamJsonAccumulator> authorAccumulators) {
        for (java.util.Map.Entry<String, ChatStreamJsonAccumulator> entry : authorAccumulators.entrySet()) {
            String author = entry.getKey();
            String phase = resolvePromptPhase(author);
            List<ChatStreamJsonAccumulator.Segment> segments = entry.getValue().flush();
            for (ChatStreamJsonAccumulator.Segment segment : segments) {
                try {
                    sendStreamSegment(emitter, phase, author, seq, segment.getContent());
                } catch (Exception ignored) {
                }
            }
        }

        try {
            JSONObject doneMsg = new JSONObject();
            doneMsg.put("seq", seq.getAndIncrement());
            doneMsg.put("phase", "done");
            doneMsg.put("author", "system");
            doneMsg.put("event", "done");
            doneMsg.put("renderable", false);
            doneMsg.put("final", true);
            JSONObject chunk = new JSONObject();
            chunk.put("type", "done");
            doneMsg.put("chunk", chunk);
            emitter.send(doneMsg.toJSONString() + "\n");
        } catch (Exception ignored) {
        }

        emitter.complete();
    }

    /**
     * @description 流式处理异常时发送错误事件并结束。
     *
     * @param emitter input HTTP 流式响应发送器
     * @param seq     input/output 流式序号计数器
     * @param error   input 异常对象
     */
    private void completeStreamWithError(ResponseBodyEmitter emitter, AtomicInteger seq, Throwable error) {
        log.error("Prompt 工程智能体流式处理异常", error);
        try {
            JSONObject errMsg = new JSONObject();
            errMsg.put("seq", seq.getAndIncrement());
            errMsg.put("phase", "error");
            errMsg.put("author", "system");
            errMsg.put("event", "error");
            errMsg.put("renderable", false);
            errMsg.put("final", true);
            JSONObject chunk = new JSONObject();
            chunk.put("type", "error");
            chunk.put("content", "Prompt 生成异常，请重试");
            errMsg.put("chunk", chunk);
            emitter.send(errMsg.toJSONString() + "\n");
        } catch (Exception ignored) {
        }
        emitter.completeWithError(error);
    }

    /**
     * @description 校验失败时发送错误消息并结束流。
     *
     * @param emitter input HTTP 流式响应发送器
     * @param message input 错误提示
     * @throws Exception output 发送失败时抛出异常
     */
    private void sendErrorAndComplete(ResponseBodyEmitter emitter, String message) throws Exception {
        JSONObject wrapper = new JSONObject();
        wrapper.put("seq", 0);
        wrapper.put("phase", "error");
        wrapper.put("author", "system");
        wrapper.put("event", "error");
        wrapper.put("renderable", false);
        wrapper.put("final", true);
        JSONObject chunk = new JSONObject();
        chunk.put("type", "error");
        chunk.put("content", message);
        wrapper.put("chunk", chunk);
        emitter.send(wrapper.toJSONString() + "\n");
        emitter.complete();
    }

    /**
     * @description 释放流式订阅资源。
     *
     * @param disposable input 流式订阅对象
     */
    private void disposeStream(io.reactivex.rxjava3.disposables.Disposable disposable) {
        if (null != disposable && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    /**
     * @description 根据 Prompt Agent 作者解析前端展示阶段。
     *
     * @param author input 当前智能体名称
     * @return output 阶段名称
     */
    private String resolvePromptPhase(String author) {
        switch (author) {
            case "prompt_requirement_analyst":
            case "prompt_context_analyzer":
                return "analyzing";
            case "prompt_architect":
                return "generating";
            case "prompt_reviewer":
                return "reviewing";
            case "prompt_output_formatter":
                return "formatting";
            default:
                return "thinking";
        }
    }

    /**
     * @description 将 NDJSON 行注入全局序号。
     *
     * @param ndjsonLine input NDJSON 行
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

    /**
     * @description 构建失败响应。
     *
     * @param code input 错误码
     * @param info input 错误信息
     * @return output 失败响应
     */
    private Response<PromptResponseDTO> failResponse(String code, String info) {
        return Response.<PromptResponseDTO>builder()
                .code(code)
                .info(info)
                .build();
    }
}
