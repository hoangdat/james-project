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

import java.util.NoSuchElementException;

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MailboxACLUpdatedEventSerializationTest {

    private static final User USER = User.fromUsername("user");
    private static final MailboxACL.EntryKey ENTRY_KEY = org.apache.james.mailbox.model.MailboxACL.EntryKey.createGroupEntryKey("any", false);
    private static final MailboxACL.Rfc4314Rights RIGHTS = new org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights(MailboxACL.Right.Administer, MailboxACL.Right.Read);
    private static final EventSerializer EVENT_SERIALIZER = new EventSerializer(new TestId.Factory());
    private static final String JSON_1 = "{" +
        "  \"MailboxACLUpdated\":{" +
        "    \"mailboxPath\":{" +
        "       \"namespace\":\"#private\"," +
        "       \"user\":\"bob\"," +
        "       \"name\":\"mailboxName\"" +
        "      }," +
        "    \"aclDiff\":{" +
        "       \"oldACL\":{\"entries\":{}}," +
        "       \"newACL\":{\"entries\":{\"$any\":\"ar\"}}}," +
        "    \"mailboxId\":\"23\"," +
        "    \"sessionId\":6," +
        "    \"user\":\"user\"" +
        "   }" +
        "}";

    private MailboxListener.MailboxACLUpdated mailboxACLUpdatedEvent;

    @BeforeEach
    void setUp() throws UnsupportedRightException {
        MailboxACL mailboxACL = MailboxACL.EMPTY.apply(
            MailboxACL.command()
                .key(ENTRY_KEY)
                .rights(RIGHTS)
                .asAddition());
        mailboxACLUpdatedEvent = new MailboxListener.MailboxACLUpdated(
            MailboxSession.SessionId.of(06),
            USER,
            new MailboxPath(MailboxConstants.USER_NAMESPACE, "bob", "mailboxName"),
            ACLDiff.computeDiff(MailboxACL.EMPTY, mailboxACL),
            TestId.of(23));
    }

    @Test
    void mailboxACLUpdatedShouldBeSerialized() {
        assertThatJson(EVENT_SERIALIZER.toJson(mailboxACLUpdatedEvent))
            .isEqualTo(JSON_1);
    }

    @Test
    void mailboxACLUpdatedShouldBeDeserialized() {
        assertThat(EVENT_SERIALIZER.fromJson(JSON_1).get())
            .isEqualTo(mailboxACLUpdatedEvent);
    }

    @Nested
    class NullUserInMailboxPath {
        private final String NULL_USER = null;
        private static final String JSON_2 = "{" +
            "  \"MailboxACLUpdated\":{" +
            "    \"mailboxPath\":{" +
            "       \"namespace\":\"#private\"," +
            "       \"name\":\"mailboxName\"" +
            "      }," +
            "    \"aclDiff\":{" +
            "       \"oldACL\":{\"entries\":{}}," +
            "       \"newACL\":{\"entries\":{\"$any\":\"ar\"}}}," +
            "    \"mailboxId\":\"23\"," +
            "    \"sessionId\":6," +
            "    \"user\":\"user\"" +
            "   }" +
            "}";

        private MailboxListener.MailboxACLUpdated mailboxACLUpdatedEvent;

        @BeforeEach
        void setUp() throws UnsupportedRightException {
            MailboxACL mailboxACL = MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition());
            mailboxACLUpdatedEvent = new MailboxListener.MailboxACLUpdated(
                MailboxSession.SessionId.of(06),
                USER,
                new MailboxPath(MailboxConstants.USER_NAMESPACE, NULL_USER, "mailboxName"),
                ACLDiff.computeDiff(MailboxACL.EMPTY, mailboxACL),
                TestId.of(23));
        }

        @Test
        void mailboxACLUpdatedShouldBeWellSerializedWithNullUser() {
            assertThatJson(EVENT_SERIALIZER.toJson(mailboxACLUpdatedEvent))
                .isEqualTo(JSON_2);
        }

        @Test
        void mailboxACLUpdatedShouldBeWellDeSerializedWithNullUser() {
            assertThat(EVENT_SERIALIZER.fromJson(JSON_2).get())
                .isEqualTo(mailboxACLUpdatedEvent);
        }
    }

    @Nested
    class DeserializationErrors {

        @Nested
        class DeserializationErrorOnSessionId {
            @Test
            void mailboxACLUpdatedShouldThrowWhenMissingSessionId() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"user\":\"bob\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"oldACL\":{\"entries\":{}}," +
                    "       \"newACL\":{\"entries\":{\"$any\":\"ar\"}}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"user\":\"user\"" +
                    "   }" +
                    "}").get())
                    .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxACLUpdatedShouldThrowWhenNullSessionId() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"user\":\"bob\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"oldACL\":{\"entries\":{}}," +
                    "       \"newACL\":{\"entries\":{\"$any\":\"ar\"}}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":null," +
                    "    \"user\":\"user\"" +
                    "   }" +
                    "}").get())
                    .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxACLUpdatedShouldThrowWhenStringSessionId() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"user\":\"bob\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"oldACL\":{\"entries\":{}}," +
                    "       \"newACL\":{\"entries\":{\"$any\":\"ar\"}}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":\"123\"," +
                    "    \"user\":\"user\"" +
                    "   }" +
                    "}").get())
                    .isInstanceOf(NoSuchElementException.class);
            }
        }

        @Nested
        class DeserializationErrorOnUser {

            @Test
            void mailboxACLUpdatedShouldThrowWhenMissingUser() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"oldACL\":{\"entries\":{}}," +
                    "       \"newACL\":{\"entries\":{\"$any\":\"ar\"}}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":6" +
                    "   }" +
                    "}").get())
                    .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxACLUpdatedShouldThrowWhenUserIsNotAString() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"oldACL\":{\"entries\":{}}," +
                    "       \"newACL\":{\"entries\":{\"$any\":\"ar\"}}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":6," +
                    "    \"user\":12345" +
                    "   }" +
                    "}").get())
                    .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxACLUpdatedShouldThrowWhenUserIsNotWellFormatted() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"oldACL\":{\"entries\":{}}," +
                    "       \"newACL\":{\"entries\":{\"$any\":\"ar\"}}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":6," +
                    "    \"user\":\"user@domain@secondDomain\"" +
                    "   }" +
                    "}").get())
                    .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Nested
        class DeserializationErrorOnACLDiff {

            @Test
            void mailboxACLUpdatedShouldThrowWhenMissingACLDiff() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":6," +
                    "    \"user\":\"user\"" +
                    "   }" +
                    "}").get())
                    .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxACLUpdatedShouldThrowWhenMissingOldACLinACLDiff() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"newACL\":{\"entries\":{\"$any\":\"ar\"}}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":6," +
                    "    \"user\":\"user\"" +
                    "   }" +
                    "}").get())
                    .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void mailboxACLUpdatedShouldThrowWhenMissingNewACLinACLDiff() {
                assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(
                    "{" +
                    "  \"MailboxACLUpdated\":{" +
                    "    \"mailboxPath\":{" +
                    "       \"namespace\":\"#private\"," +
                    "       \"name\":\"mailboxName\"" +
                    "      }," +
                    "    \"aclDiff\":{" +
                    "       \"oldACL\":{\"entries\":{}}}," +
                    "    \"mailboxId\":\"23\"," +
                    "    \"sessionId\":6," +
                    "    \"user\":\"user\"" +
                    "   }" +
                    "}").get())
                    .isInstanceOf(NoSuchElementException.class);
            }
        }
    }
}
