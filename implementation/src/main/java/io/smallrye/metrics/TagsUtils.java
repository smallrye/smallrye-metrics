package io.smallrye.metrics;

import org.eclipse.microprofile.metrics.Tag;

public class TagsUtils {

    private static final String MALFORMED_TAGS = "Malformed list of tags";

    public static Tag parseTag(String kvString) {
        if (kvString == null || kvString.isEmpty() || !kvString.contains("=")) {
            throw new IllegalArgumentException("Not a k=v pair: " + kvString);
        }
        String[] kv = kvString.split("=");
        if (kv.length != 2) {
            throw new IllegalArgumentException("Not a k=v pair: " + kvString);
        }
        String key = kv[0].trim();
        String value = kv[1].trim();
        return new Tag(key, value);
    }

    public static Tag[] parseTagsAsArray(String[] kvStrings) {
        Tag[] result = new Tag[kvStrings.length];
        int i = 0;
        for (String kvString : kvStrings) {
            result[i] = parseTag(kvString);
            i++;
        }
        return result;
    }

}
