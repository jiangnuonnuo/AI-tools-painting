package cn.bugstack.ai.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * description: 智能体对话统一响应对象，type 标识响应类型，content 承载前端渲染主数据，metadata 承载可选辅助信息。
 */
@Data
public class ChatResponseDTO {

    private String type;
    private Object content;
    private Map<String, Object> metadata;

}
