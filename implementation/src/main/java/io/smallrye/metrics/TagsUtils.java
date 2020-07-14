/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.smallrye.metrics;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.microprofile.metrics.Tag;

public class TagsUtils {

    private TagsUtils() {
        // not to be constructed
    }

    public static Tag parseTag(String kvString) {
        if (kvString == null || kvString.isEmpty() || !kvString.contains("=")) {
            throw SmallRyeMetricsMessages.msg.notAKeyValuePair(kvString);
        }
        String[] kv = kvString.split("=");
        if (kv.length != 2) {
            throw SmallRyeMetricsMessages.msg.notAKeyValuePair(kvString);
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

    private static final String GLOBAL_TAG_MALFORMED_EXCEPTION = "Malformed list of Global Tags. Tag names "
            + "must match the following regex [a-zA-Z_][a-zA-Z0-9_]*."
            + " Global Tag values must not be empty."
            + " Global Tag values MUST escape equal signs `=` and commas `,`"
            + " with a backslash `\\` ";

    /**
     * Parses the global tags retrieved from environment variable {@code MP_METRICS_TAGS}.
     *
     * @param globalTags the string of global tags retrieved from MP_METRICS_TAGS
     * @throws IllegalArgumentException if the global tags list does not adhere to
     *         the appropriate format.
     */
    public static Map<String, String> parseGlobalTags(String globalTags) throws IllegalArgumentException {
        if (globalTags == null || globalTags.length() == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> tags = new TreeMap<String, String>();
        String[] kvPairs = globalTags.split("(?<!\\\\),");
        for (String kvString : kvPairs) {

            if (kvString.length() == 0) {
                throw new IllegalArgumentException(GLOBAL_TAG_MALFORMED_EXCEPTION);
            }

            String[] keyValueSplit = kvString.split("(?<!\\\\)=");

            if (keyValueSplit.length != 2 || keyValueSplit[0].length() == 0 || keyValueSplit[1].length() == 0) {
                throw new IllegalArgumentException(GLOBAL_TAG_MALFORMED_EXCEPTION);
            }

            String key = keyValueSplit[0];
            String value = keyValueSplit[1];

            if (!key.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                throw new IllegalArgumentException("Invalid Tag name. Tag names must match the following regex "
                        + "[a-zA-Z_][a-zA-Z0-9_]*");
            }
            value = value.replace("\\,", ",");
            value = value.replace("\\=", "=");
            tags.put(key, value);
        }
        return tags;
    }

}
