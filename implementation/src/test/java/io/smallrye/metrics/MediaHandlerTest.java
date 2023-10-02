/*
 * Copyright 2018, 2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.smallrye.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author hrupp
 */
public class MediaHandlerTest {

    MetricsRequestHandler requestHandler;

    @BeforeEach
    public void setUp() {

        requestHandler = new MetricsRequestHandler();
    }

    @Test
    public void testNotSamePrio() {
        Optional<String> res = requestHandler
                .getBestMatchingMediaType(Stream.of("application/json;q=0.1", "text/plain;q=0.9"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo("text/plain");

    }

    @Test
    public void testNotSamePrio2() {
        Optional<String> res = requestHandler
                .getBestMatchingMediaType(Stream.of("application/json;q=0.1,text/plain;q=0.9"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo("text/plain");
    }

    @Test
    public void testSamePrio() {
        Optional<String> res = requestHandler
                .getBestMatchingMediaType(Stream.of("application/json;q=0.5", "text/plain;q=0.5"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo("text/plain");
    }

    @Test
    public void testSamePrio2() {
        Optional<String> res = requestHandler
                .getBestMatchingMediaType(Stream.of("text/plain;q=0.5", "application/json;q=0.5"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo("text/plain");
    }

    @Test
    public void testNoMatch() {
        Optional<String> res = requestHandler.getBestMatchingMediaType(Stream.of("image/png", "image/jpeg "));
        assertThat(res.isPresent()).isFalse();
    }

    @Test
    public void testBoth() {
        Optional<String> res = requestHandler
                .getBestMatchingMediaType(Stream.of("application/json;q=0.1", "text/plain;q=0.9"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo(("text/plain"));
    }

    @Test
    public void testBoth2() {
        Optional<String> res = requestHandler
                .getBestMatchingMediaType(Stream.of("application/json;q=0.1,text/plain;q=0.9"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo(("text/plain"));
    }

    @Test
    public void testBoth3() {
        Optional<String> res = requestHandler
                .getBestMatchingMediaType(Stream.of("application/json;q=0.8", "text/plain;q=0.1"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo("text/plain");
    }

    @Test
    public void testBoth4() {
        Optional<String> res = requestHandler
                .getBestMatchingMediaType(Stream.of("application/json;q=0.8", "text/plain;q=0.5", "*/*;q=0.1"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo("text/plain");
    }

    @Test
    public void testStarStarOnly() {
        Optional<String> res = requestHandler.getBestMatchingMediaType(Stream.of("*/*"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo("text/plain");
    }

    @Test
    public void testStarMix() {
        Optional<String> res = requestHandler.getBestMatchingMediaType(Stream.of("*/*;q=0.1", "image/png;q=1"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo("text/plain");
    }

    @Test
    public void testStarMix2() {
        Optional<String> res = requestHandler.getBestMatchingMediaType(Stream.of("image/png;q=1", "*/*;q=0.1"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo("text/plain");
    }

    @Test
    public void testWithMissingPrioritySingle() {
        Optional<String> res = requestHandler.getBestMatchingMediaType(Stream.of("application/json;charset=UTF-8"));
        assertThat(res.isPresent()).isFalse();
    }

    @Test
    public void testWithMissingPriorityMulti() {
        // default q=1 so json should be preferred
        Optional<String> res = requestHandler.getBestMatchingMediaType(
                Stream.of("text/plain;q=0.8;charset=UTF-8", "application/json;charset=UTF-8"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo("text/plain");
    }

    @Test
    public void testDefaultMediaTypeWhenAcceptHeaderNotSet() {
        Optional<String> res = requestHandler.getBestMatchingMediaType(Stream.empty());
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo("text/plain");
    }

    @Test
    public void testMediaTypeWhenAcceptValuesHasSpacest() {
        Optional<String> res = requestHandler.getBestMatchingMediaType(
                Stream.of("   text/html", "    image/gif", "  image/jpeg", " *; q=.2", "    */*; q=.2"));
        assertThat(res.isPresent()).isTrue();
        assertThat(res.get()).isEqualTo("text/plain");
    }

}
