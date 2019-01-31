package io.smallrye.metrics;

import org.eclipse.microprofile.metrics.Tag;

import java.util.ArrayList;
import java.util.List;

public class TagsUtils {

    private static final String MALFORMED_TAGS = "Malformed list of tags";

    // TODO: do we need this at all?
/*    public static List<Tag> parseGlobalTags(String tagsString) throws IllegalArgumentException {
        List<Tag> tags = new ArrayList<>();
        if (tagsString == null || tagsString.length() == 0) {
            return tags;
        }
        String[] kvPairs = tagsString.split("(?<!\\\\),");
        for (String kvString : kvPairs) {

            if (kvString.length() == 0) {
                throw new IllegalArgumentException(MALFORMED_TAGS);
            }

            String[] keyValueSplit = kvString.split("(?<!\\\\)=");

            if (keyValueSplit.length != 2 || keyValueSplit[0].length() == 0 || keyValueSplit[1].length() == 0) {
                throw new IllegalArgumentException(MALFORMED_TAGS);
            }

            String key = keyValueSplit[0];
            String value = keyValueSplit[1];

            if (!key.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                throw new IllegalArgumentException("Invalid Tag name. Tag names must match the following regex "
                        + "[a-zA-Z_][a-zA-Z0-9_]*");
            }
            value = value.replace("\\,", ",");
            value = value.replace("\\=", "=");
            tags.add(new Tag(key, value));
        }
        return tags;
    }*/

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
