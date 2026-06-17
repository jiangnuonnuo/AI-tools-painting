package cn.bugstack.ai.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * @author xerina
 * @description Prompt 工程智能体响应 DTO，返回会话编号、Prompt 类型、完整 Prompt 文本和辅助元数据。
 */
@Data
public class PromptResponseDTO {

    private String sessionId;

    private String type;

    private Object content;

    private Map<String, Object> metadata;

}
