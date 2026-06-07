package cn.bugstack.ai.domain.agent.service.armory.matter.patch;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;

public class CustomApiWebClientFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String customBaseUrl = request.headers().getFirst("X-Custom-Base-Url");
        String customApiKey = request.headers().getFirst("X-Custom-Api-Key");

        if (customBaseUrl != null || customApiKey != null) {
            ClientRequest.Builder builder = ClientRequest.from(request);
            if (customBaseUrl != null) {
                try {
                    URI originalUri = request.url();
                    URI customUri = new URI(customBaseUrl);
                    String customPath = customUri.getPath();
                    if (customPath == null) customPath = "";
                    String path = originalUri.getPath();
                    if (customPath.endsWith("/v1") && path.startsWith("/v1")) {
                        customPath = customPath.substring(0, customPath.length() - 3);
                    }
                    String newPath = customPath + path;
                    newPath = newPath.replaceAll("//+", "/");
                    URI newUri = new URI(customUri.getScheme(), customUri.getUserInfo(), customUri.getHost(), customUri.getPort(), newPath, originalUri.getQuery(), originalUri.getFragment());
                    builder.url(newUri);
                } catch (Exception e) {
                    // ignore
                }
            }
            builder.headers(headers -> {
                headers.remove("X-Custom-Base-Url");
                headers.remove("X-Custom-Api-Key");
                if (customApiKey != null && !customApiKey.isEmpty()) {
                    headers.set("Authorization", "Bearer " + customApiKey);
                }
            });
            return next.exchange(builder.build());
        }

        return next.exchange(request);
    }
}
