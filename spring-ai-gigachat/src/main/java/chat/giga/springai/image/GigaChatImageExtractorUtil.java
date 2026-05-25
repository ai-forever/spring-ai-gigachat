package chat.giga.springai.image;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GigaChatImageExtractorUtil {
    private static final Pattern IMG_ID_PATTERN = Pattern.compile("<img\\s+src=\"([a-fA-F0-9\\-]{36})\"");

    public static Set<String> extract(String content) {
        Set<String> fileIds = new HashSet<>();

        if (content != null) {
            Matcher matcher = IMG_ID_PATTERN.matcher(content);

            while (matcher.find()) {
                String fileId = matcher.group(1);
                fileIds.add(fileId);
            }
        }

        return fileIds;
    }
}
