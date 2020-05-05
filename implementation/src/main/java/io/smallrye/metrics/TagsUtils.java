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

}
