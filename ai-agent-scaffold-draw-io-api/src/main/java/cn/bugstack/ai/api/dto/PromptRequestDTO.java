package cn.bugstack.ai.api.dto;

import lombok.Data;

/**
 * @author xerina
 * @description Prompt 工程智能体请求 DTO，承载新建 Prompt、整体改写 Prompt、局部改写 Prompt 的输入参数。
 */
@Data
public class PromptRequestDTO {

    private String agentId;

    private String userId;

    private String sessionId;

    private String mode;

    private String taskType;

    private String goal;

    private String currentPrompt;

    private String selectedPromptText;

    private String editInstruction;

    private String constraints;

    private String outputFormat;

    private String customBaseUrl;

    private String customApiKey;

    private String customCompletionsPath;

    private String customModel;

}
