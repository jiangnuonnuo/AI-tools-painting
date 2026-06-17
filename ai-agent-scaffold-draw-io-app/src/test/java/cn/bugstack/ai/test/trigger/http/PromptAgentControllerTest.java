package cn.bugstack.ai.test.trigger.http;

import cn.bugstack.ai.api.dto.PromptRequestDTO;
import cn.bugstack.ai.api.dto.PromptResponseDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.service.IChatService;
import cn.bugstack.ai.trigger.http.PromptAgentController;
import cn.bugstack.ai.types.enums.ResponseCode;
import com.alibaba.fastjson.JSON;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xerina
 * @description PromptAgentController 单元测试，验证 Prompt 生成、整体改写、局部改写的参数契约。
 */
public class PromptAgentControllerTest {

    /**
     * @description 验证 generate 模式不要求当前 Prompt，只根据目标生成新 Prompt。
     *
     * @return output 测试断言结果
     */
    @Test
    public void test_generatePrompt_should_allow_generate_without_currentPrompt() {
        FakeChatService chatService = new FakeChatService();
        PromptAgentController controller = buildController(chatService);

        PromptRequestDTO requestDTO = new PromptRequestDTO();
        requestDTO.setAgentId("300002");
        requestDTO.setUserId("xerina");
        requestDTO.setMode("generate");
        requestDTO.setTaskType("backend");
        requestDTO.setGoal("生成后端实现计划");
        requestDTO.setConstraints("遵循 DDD");

        Response<PromptResponseDTO> response = controller.generatePrompt(requestDTO);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertEquals("prompt", response.getData().getType());
        Assert.assertEquals("session-001", response.getData().getSessionId());
        Assert.assertEquals("最终 Prompt", response.getData().getContent());
        Assert.assertTrue(chatService.lastMessage.contains("\"mode\":\"generate\""));
        Assert.assertTrue(chatService.lastMessage.contains("\"goal\":\"生成后端实现计划\""));
        Assert.assertFalse(chatService.lastMessage.contains("currentDrawioXml"));
    }

    /**
     * @description 验证 rewrite 模式缺少 currentPrompt 时返回非法参数。
     *
     * @return output 测试断言结果
     */
    @Test
    public void test_generatePrompt_should_reject_rewrite_without_currentPrompt() {
        FakeChatService chatService = new FakeChatService();
        PromptAgentController controller = buildController(chatService);

        PromptRequestDTO requestDTO = new PromptRequestDTO();
        requestDTO.setAgentId("300002");
        requestDTO.setUserId("xerina");
        requestDTO.setMode("rewrite");
        requestDTO.setEditInstruction("调整为更严谨");

        Response<PromptResponseDTO> response = controller.generatePrompt(requestDTO);

        Assert.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        Assert.assertNull(response.getData());
        Assert.assertNull(chatService.lastMessage);
    }

    /**
     * @description 验证 partial_rewrite 模式会携带当前 Prompt、选中文本和修改要求。
     *
     * @return output 测试断言结果
     */
    @Test
    public void test_generatePrompt_should_accept_partialRewrite_with_selectedPromptText() {
        FakeChatService chatService = new FakeChatService();
        PromptAgentController controller = buildController(chatService);

        PromptRequestDTO requestDTO = new PromptRequestDTO();
        requestDTO.setAgentId("300002");
        requestDTO.setUserId("xerina");
        requestDTO.setSessionId("session-existing");
        requestDTO.setMode("partial_rewrite");
        requestDTO.setCurrentPrompt("你是开发工程师。输出代码。");
        requestDTO.setSelectedPromptText("输出代码");
        requestDTO.setEditInstruction("改为先输出实现计划");

        Response<PromptResponseDTO> response = controller.generatePrompt(requestDTO);

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assert.assertEquals("session-existing", response.getData().getSessionId());
        Assert.assertTrue(chatService.lastMessage.contains("\"mode\":\"partial_rewrite\""));
        Assert.assertTrue(chatService.lastMessage.contains("\"currentPrompt\":\"你是开发工程师。输出代码。\""));
        Assert.assertTrue(chatService.lastMessage.contains("\"selectedPromptText\":\"输出代码\""));
        Assert.assertTrue(chatService.lastMessage.contains("\"editInstruction\":\"改为先输出实现计划\""));
    }

    /**
     * @description 验证 partial_rewrite 模式缺少 selectedPromptText 时返回非法参数。
     *
     * @return output 测试断言结果
     */
    @Test
    public void test_generatePrompt_should_reject_partialRewrite_without_selectedPromptText() {
        FakeChatService chatService = new FakeChatService();
        PromptAgentController controller = buildController(chatService);

        PromptRequestDTO requestDTO = new PromptRequestDTO();
        requestDTO.setAgentId("300002");
        requestDTO.setUserId("xerina");
        requestDTO.setMode("partial_rewrite");
        requestDTO.setCurrentPrompt("你是开发工程师。输出代码。");
        requestDTO.setEditInstruction("改为先输出实现计划");

        Response<PromptResponseDTO> response = controller.generatePrompt(requestDTO);

        Assert.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        Assert.assertNull(response.getData());
        Assert.assertNull(chatService.lastMessage);
    }

    /**
     * @description 构建被测 Controller 并注入假的对话服务。
     *
     * @param chatService input 假的对话服务
     * @return output 被测 Controller
     */
    private PromptAgentController buildController(FakeChatService chatService) {
        PromptAgentController controller = new PromptAgentController();
        ReflectionTestUtils.setField(controller, "chatService", chatService);
        return controller;
    }

    /**
     * @author xerina
     * @description 用于 Controller 单元测试的 IChatService 假实现，记录最后一次调用参数。
     */
    private static class FakeChatService implements IChatService {

        private String lastMessage;

        @Override
        public List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList() {
            return new ArrayList<>();
        }

        @Override
        public String createSession(String agentId, String userId) {
            return "session-001";
        }

        @Override
        public List<String> handleMessage(String agentId, String userId, String message) {
            this.lastMessage = message;
            return responseMessages();
        }

        @Override
        public List<String> handleMessage(String agentId, String userId, String sessionId, String message) {
            this.lastMessage = message;
            return responseMessages();
        }

        @Override
        public Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message) {
            this.lastMessage = message;
            return Flowable.empty();
        }

        @Override
        public List<String> handleMessage(cn.bugstack.ai.domain.agent.model.entity.ChatCommandEntity chatCommandEntity) {
            return responseMessages();
        }

        /**
         * @description 构建 Prompt 类型模型输出。
         *
         * @return output 模型输出列表
         */
        private List<String> responseMessages() {
            com.alibaba.fastjson.JSONObject metadata = new com.alibaba.fastjson.JSONObject();
            metadata.put("backendContent", "已生成 Prompt。");

            com.alibaba.fastjson.JSONObject response = new com.alibaba.fastjson.JSONObject();
            response.put("type", "prompt");
            response.put("content", "最终 Prompt");
            response.put("metadata", metadata);

            return List.of(JSON.toJSONString(response));
        }
    }
}
