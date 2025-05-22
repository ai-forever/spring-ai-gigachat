package chat.giga.springai.tool.definition;

import chat.giga.springai.tool.support.GigaToolUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Linar Abzaltdinov
 */
@Getter
@EqualsAndHashCode
@ToString
public class FewShotExample {
    // User request
    private final String request;
    // Function input example corresponding to user request.
    private final String params;

    private FewShotExample(String request, String params) {
        this.request = request;
        this.params = params;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String request;
        private String params;

        public Builder request(String request) {
            this.request = request;
            return this;
        }

        public Builder params(Object params) {
            this.params = GigaToolUtils.toJson(params);
            return this;
        }

        public Builder paramsSchema(String paramsSchema) {
            this.params = paramsSchema;
            return this;
        }

        public FewShotExample build() {
            return new FewShotExample(request, params);
        }
    }
}
