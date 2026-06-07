package cn.bugstack.ai.domain.agent.service.armory.matter.plugin;

import cn.bugstack.ai.domain.agent.service.chat.CustomApiConfigManager;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HttpOptions;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service("customConfigPlugin")
public class CustomConfigPlugin extends BasePlugin {

    public CustomConfigPlugin() {
        super("CustomConfigPlugin");
    }

    @Override
    public Maybe<LlmResponse> beforeModelCallback(CallbackContext context, LlmRequest.Builder requestBuilder) {
        String sessionId = context.sessionId();
        CustomApiConfigManager.CustomApiConfig config = CustomApiConfigManager.getConfig(sessionId);

        if (config != null) {
            GenerateContentConfig.Builder configBuilder;
            if (requestBuilder.config().isPresent()) {
                configBuilder = requestBuilder.config().get().toBuilder();
            } else {
                configBuilder = GenerateContentConfig.builder();
            }

            HttpOptions.Builder httpOptionsBuilder;
            if (configBuilder.build().httpOptions().isPresent()) {
                httpOptionsBuilder = configBuilder.build().httpOptions().get().toBuilder();
            } else {
                httpOptionsBuilder = HttpOptions.builder();
            }

            Map<String, String> headers = new HashMap<>();
            if (httpOptionsBuilder.build().headers().isPresent()) {
                headers.putAll(httpOptionsBuilder.build().headers().get());
            }

            if (StringUtils.isNotBlank(config.getBaseUrl())) {
                headers.put("X-Custom-Base-Url", config.getBaseUrl());
            }
            if (StringUtils.isNotBlank(config.getApiKey())) {
                headers.put("X-Custom-Api-Key", config.getApiKey());
            }
            if (StringUtils.isNotBlank(config.getCompletionsPath())) {
                headers.put("X-Custom-Completions-Path", config.getCompletionsPath());
            }
            if (StringUtils.isNotBlank(config.getModel())) {
                requestBuilder.model(config.getModel());
            }

            httpOptionsBuilder.headers(headers);
            configBuilder.httpOptions(httpOptionsBuilder.build());
            requestBuilder.config(configBuilder.build());
        }

        return super.beforeModelCallback(context, requestBuilder);
    }
}
