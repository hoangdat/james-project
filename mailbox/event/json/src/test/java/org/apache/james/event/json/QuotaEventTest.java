/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.event.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QuotaEventTest {

    private static final User USER = User.fromUsername("user");
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("foo", Optional.empty());
    private static final Quota<QuotaCount> QUOTA_COUNT = Quota.<QuotaCount>builder()
        .used(QuotaCount.count(12))
        .computedLimit(QuotaCount.count(100))
        .build();
    private static final Quota<QuotaSize> QUOTA_SIZE = Quota.<QuotaSize>builder()
        .used(QuotaSize.size(1234))
        .computedLimit(QuotaSize.size(10000))
        .build();
    private static final Instant INSTANT = Instant.parse("2018-11-13T12:00:55Z");

    @Nested
    class WithUser {

        @Nested
        class WithValidUser {

            @Nested
            class WithUserContainsOnlyUsername {

                private MailboxListener.QuotaUsageUpdatedEvent eventWithUserContainsUsername = new MailboxListener.QuotaUsageUpdatedEvent(
                    User.fromUsername("onlyUsername"),
                    QUOTA_ROOT,
                    QUOTA_COUNT,
                    QUOTA_SIZE,
                    INSTANT);
                private String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"onlyUsername\"" +
                        "}" +
                    "}";

                @Test
                void fromJsonShouldReturnQuotaEvent() {
                    assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).get())
                        .isEqualTo(eventWithUserContainsUsername);
                }

                @Test
                void toJsonShouldReturnQuotaEventJson() {
                    assertThatJson(QuotaEvent$.MODULE$.toJson(eventWithUserContainsUsername))
                        .isEqualTo(quotaUsageUpdatedEvent);
                }
            }

            @Nested
            class WithUserContainsUsernameAndDomain {

                private MailboxListener.QuotaUsageUpdatedEvent eventWithUserContainsUsernameAndDomain = new MailboxListener.QuotaUsageUpdatedEvent(
                    User.fromUsername("user@domain"),
                    QUOTA_ROOT,
                    QUOTA_COUNT,
                    QUOTA_SIZE,
                    INSTANT);
                private String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"user@domain\"" +
                        "}" +
                    "}";

                @Test
                void fromJsonShouldReturnQuotaEvent() {
                    assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).get())
                        .isEqualTo(eventWithUserContainsUsernameAndDomain);
                }

                @Test
                void toJsonShouldReturnQuotaEventJson() {
                    assertThatJson(QuotaEvent$.MODULE$.toJson(eventWithUserContainsUsernameAndDomain))
                        .isEqualTo(quotaUsageUpdatedEvent);
                }
            }
        }

        @Nested
        class WithInvalidUser {

            @Test
            void fromJsonShouldThrowWhenEmptyUser() {
                String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"\"" +
                        "}" +
                    "}";
                assertThatThrownBy(() -> QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent))
                    .isInstanceOf(IllegalArgumentException.class);
            }


            @Test
            void fromJsonShouldReturnErrorResultWhenUserIsNull() {
                String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}" +
                        "}" +
                    "}";

                assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).isSuccess())
                    .isFalse();
            }

            @Test
            void fromJsonShouldThrowWhenUserIsInvalid() {
                String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"@domain\"" +
                        "}" +
                    "}";
                assertThatThrownBy(() -> QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent))
                    .isInstanceOf(IllegalArgumentException.class);
            }
        }

    }

    @Nested
    class WitQuotaRoot {

        @Nested
        class WithEmptyQuotaRoot {
            private final QuotaRoot emptyQuotaRoot = QuotaRoot.quotaRoot("", Optional.empty());
            private MailboxListener.QuotaUsageUpdatedEvent eventWithEmptyQuotaRoot =
                new MailboxListener.QuotaUsageUpdatedEvent(
                    USER,
                    emptyQuotaRoot,
                    QUOTA_COUNT,
                    QUOTA_SIZE,
                    INSTANT);
            private String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"quotaRoot\":\"\"," +
                    "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                    "\"time\":\"2018-11-13T12:00:55Z\"," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            @Test
            void toJsonShouldSerializeWithEmptyQuotaRoot() {
                assertThatJson(QuotaEvent$.MODULE$.toJson(eventWithEmptyQuotaRoot))
                    .isEqualTo(quotaUsageUpdatedEvent);
            }

            @Test
            void fromJsonShouldDeserializeWithEmptyQuotaRoot() {
                assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).get())
                    .isEqualTo(eventWithEmptyQuotaRoot);
            }
        }

        @Nested
        class WithNullQuotaRoot {
            private MailboxListener.QuotaUsageUpdatedEvent eventWithNullQuotaRoot =
                new MailboxListener.QuotaUsageUpdatedEvent(
                    USER,
                    null,
                    QUOTA_COUNT,
                    QUOTA_SIZE,
                    INSTANT);

            private String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                    "\"time\":\"2018-11-13T12:00:55Z\"," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            @Test
            void toJsonShouldThrowWithNullQuotaRoot() {
                assertThatThrownBy(() -> QuotaEvent$.MODULE$.toJson(eventWithNullQuotaRoot))
                    .isInstanceOf(NullPointerException.class);
            }

            @Test
            void fromJsonShouldReturnErrorWithNullQuotaRoot() {
                assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).isSuccess())
                    .isFalse();
            }
        }
    }

    @Nested
    class WithQuotaCount {

        @Nested
        class WithQuotaCountLimitInDomainScope {
            private String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"quotaRoot\":\"foo\"," +
                    "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{\"Domain\":100}}," +
                    "\"time\":\"2018-11-13T12:00:55Z\"," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            private Quota<QuotaCount> quotaCount = Quota.<QuotaCount>builder()
                .used(QuotaCount.count(12))
                .computedLimit(QuotaCount.count(100))
                .limitForScope(QuotaCount.count(100), Quota.Scope.Domain)
                .build();

            private MailboxListener.QuotaUsageUpdatedEvent eventWithLimitedQuotaCount =
                new MailboxListener.QuotaUsageUpdatedEvent(
                    USER,
                    QUOTA_ROOT,
                    quotaCount,
                    QUOTA_SIZE,
                    INSTANT);

            @Test
            void toJsonShouldSerializeWithLimitQuotaCountInDomain() {
                assertThatJson(QuotaEvent$.MODULE$.toJson(eventWithLimitedQuotaCount))
                    .isEqualTo(quotaUsageUpdatedEvent);
            }

            @Test
            void fromJsonShouldDeserializeWithLimitQuotaCountInDomain() {
                assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).get())
                    .isEqualTo(eventWithLimitedQuotaCount);
            }
        }

        @Nested
        class WithQuotaCountUnlimitedInGlobalScope {
            private String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"quotaRoot\":\"foo\"," +
                    "\"countQuota\":{\"used\":12,\"limit\":null,\"limits\":{\"Global\":null}}," +
                    "\"time\":\"2018-11-13T12:00:55Z\"," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            private Quota<QuotaCount> quotaCount = Quota.<QuotaCount>builder()
                .used(QuotaCount.count(12))
                .computedLimit(QuotaCount.unlimited())
                .limitForScope(QuotaCount.unlimited(), Quota.Scope.Global)
                .build();

            private MailboxListener.QuotaUsageUpdatedEvent eventWithUnlimitedQuotaCount =
                new MailboxListener.QuotaUsageUpdatedEvent(
                    USER,
                    QUOTA_ROOT,
                    quotaCount,
                    QUOTA_SIZE,
                    INSTANT);

            @Test
            void toJsonShouldSerializeWithLimitQuotaCountInDomain() {
                assertThatJson(QuotaEvent$.MODULE$.toJson(eventWithUnlimitedQuotaCount))
                    .isEqualTo(quotaUsageUpdatedEvent);
            }

            @Test
            void fromJsonShouldDeserializeWithLimitQuotaCountInDomain() {
                assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).get())
                    .isEqualTo(eventWithUnlimitedQuotaCount);
            }
        }
    }

    @Nested
    class WithQuotaSize {

        @Nested
        class WithQuotaSizeLimitInUserScope {
            private String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"quotaRoot\":\"foo\"," +
                    "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                    "\"time\":\"2018-11-13T12:00:55Z\"," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":123456,\"limits\":{\"User\":123456}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            private Quota<QuotaSize> quotaSize = Quota.<QuotaSize>builder()
                .used(QuotaSize.size(1234))
                .computedLimit(QuotaSize.size(123456))
                .limitForScope(QuotaSize.size(123456), Quota.Scope.User)
                .build();

            private MailboxListener.QuotaUsageUpdatedEvent eventWithLimitedQuotaSize =
                new MailboxListener.QuotaUsageUpdatedEvent(
                    USER,
                    QUOTA_ROOT,
                    QUOTA_COUNT,
                    quotaSize,
                    INSTANT);

            @Test
            void toJsonShouldSerializeWithLimitQuotaSizeInUser() {
                assertThatJson(QuotaEvent$.MODULE$.toJson(eventWithLimitedQuotaSize))
                    .isEqualTo(quotaUsageUpdatedEvent);
            }

            @Test
            void fromJsonShouldDeserializeWithLimitQuotaSizeInUser() {
                assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).get())
                    .isEqualTo(eventWithLimitedQuotaSize);
            }
        }
    }

    @Nested
    class WithQuotaSizeUnlimitedInUserScope {

        private String quotaUsageUpdatedEvent =
            "{" +
                "\"QuotaUsageUpdatedEvent\":{" +
                "\"quotaRoot\":\"foo\"," +
                "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                "\"time\":\"2018-11-13T12:00:55Z\"," +
                "\"sizeQuota\":{\"used\":1234,\"limit\":null,\"limits\":{\"User\":null}}," +
                "\"user\":\"user\"" +
                "}" +
            "}";

        private Quota<QuotaSize> quotaSize = Quota.<QuotaSize>builder()
            .used(QuotaSize.size(1234))
            .computedLimit(QuotaSize.unlimited())
            .limitForScope(QuotaSize.unlimited(), Quota.Scope.User)
            .build();

        private MailboxListener.QuotaUsageUpdatedEvent eventWithUnlimitedQuotaSize =
            new MailboxListener.QuotaUsageUpdatedEvent(
                USER,
                QUOTA_ROOT,
                QUOTA_COUNT,
                quotaSize,
                INSTANT);

        @Test
        void toJsonShouldSerializeWithUnlimitedQuotaSizeInUser() {
            assertThatJson(QuotaEvent$.MODULE$.toJson(eventWithUnlimitedQuotaSize))
                .isEqualTo(quotaUsageUpdatedEvent);
        }

        @Test
        void fromJsonShouldDeserializeWithUnlimitedQuotaSizeInUser() {
            assertThat(QuotaEvent$.MODULE$.fromJson(quotaUsageUpdatedEvent).get())
                .isEqualTo(eventWithUnlimitedQuotaSize);
        }
    }
}
