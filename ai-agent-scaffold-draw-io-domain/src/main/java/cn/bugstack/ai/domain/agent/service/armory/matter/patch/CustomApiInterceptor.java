package cn.bugstack.ai.domain.agent.service.armory.matter.patch;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;

import java.io.IOException;
import java.net.URI;

public class CustomApiInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();
        String customBaseUrl = headers.getFirst("X-Custom-Base-Url");
        String customApiKey = headers.getFirst("X-Custom-Api-Key");

        HttpRequest modifiedRequest = request;

        if (customBaseUrl != null || customApiKey != null) {
            modifiedRequest = new HttpRequestWrapper(request) {
                @Override
                public URI getURI() {
                    URI originalUri = super.getURI();
                    if (customBaseUrl != null) {
                        try {
                            String path = originalUri.getPath();
                            URI customUri = new URI(customBaseUrl);
                            String customPath = customUri.getPath();
                            if (customPath == null) customPath = "";
                            // 避免重复的 /v1
                            if (customPath.endsWith("/v1") && path.startsWith("/v1")) {
                                customPath = customPath.substring(0, customPath.length() - 3);
                            }
                            String newPath = customPath + path;
                            newPath = newPath.replaceAll("//+", "/");
                            return new URI(customUri.getScheme(), customUri.getUserInfo(), customUri.getHost(), customUri.getPort(), newPath, originalUri.getQuery(), originalUri.getFragment());
                        } catch (Exception e) {
                            return originalUri;
                        }
                    }
                    return originalUri;
                }

                @Override
                public HttpHeaders getHeaders() {
                    HttpHeaders modifiedHeaders = new HttpHeaders();
                    modifiedHeaders.putAll(super.getHeaders());
                    modifiedHeaders.remove("X-Custom-Base-Url");
                    modifiedHeaders.remove("X-Custom-Api-Key");
                    if (customApiKey != null && !customApiKey.isEmpty()) {
                        modifiedHeaders.set("Authorization", "Bearer " + customApiKey);
                    }
                    return modifiedHeaders;
                }
            };
        }

        return execution.execute(modifiedRequest, body);
    }
}
