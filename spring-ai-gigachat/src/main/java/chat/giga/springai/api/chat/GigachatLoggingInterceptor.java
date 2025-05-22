package chat.giga.springai.api.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

@Slf4j
@SuppressWarnings("NullableProblems")
public class GigachatLoggingInterceptor implements ClientHttpRequestInterceptor {

    private final ObjectMapper objectMapper;

    public GigachatLoggingInterceptor() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!log.isDebugEnabled()) return execution.execute(request, body);

        final ClientHttpResponse response = new BufferingClientHttpResponseWrapper(execution.execute(request, body));
        log.debug(
                """
                        \n\tRequest URL:\s
                        {} \

                        \tRequest headers:\s
                        {} \

                        \tRequest body:\s
                        {} \

                        \tResponse headers:\s
                        {} \

                        \tResponse body:\s
                        {}
                        """,
                request.getURI(),
                formatHeaders(request.getHeaders()),
                formatJson(new String(body)),
                formatHeaders(response.getHeaders()),
                formatJson(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));

        return response;
    }

    /**
     * Функция форматирования json через object mapper, не очень производительно, зато не тащим в проект доп библиотеки
     *
     * @param json json для форматирования
     * @return отформатированный json, или json без форматирования в случае некорректной структуры
     */
    private String formatJson(String json) {
        try {
            final Object validJson = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(validJson);
        } catch (Exception ignored) {
            return json;
        }
    }

    /**
     * Функция форматирования HttpHeaders в виде строки
     *
     * @param headers хедеры для форматирования
     * @return строка с отформатированными хедерами
     */
    private String formatHeaders(HttpHeaders headers) {
        final StringBuilder stringBuilder = new StringBuilder();
        headers.forEach((k, v) -> stringBuilder.append(k).append(": ").append(v).append("\n"));
        String string = stringBuilder.toString();
        if (string.endsWith("\n")) {
            string = string.substring(0, string.length() - 1);
        }
        return string;
    }

    /**
     * Нужен для кэширования тела ответа,
     * т.к. по дефолту тело ответа имеет тип InputStream и может быть вычитано только один раз
     */
    public static class BufferingClientHttpResponseWrapper implements ClientHttpResponse {

        private final ClientHttpResponse response;
        private byte[] body;

        public BufferingClientHttpResponseWrapper(ClientHttpResponse response) {
            this.response = response;
        }

        @Override
        public InputStream getBody() throws IOException {
            if (body == null) {
                body = StreamUtils.copyToByteArray(response.getBody());
            }
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return this.response.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return this.response.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.response.getHeaders();
        }

        @Override
        public void close() {
            this.response.close();
        }
    }
}
