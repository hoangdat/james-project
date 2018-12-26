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

package org.apache.james.mailbox.events;

import static org.apache.james.mailbox.events.EventBusContract.ALL_GROUPS;
import static org.apache.james.mailbox.events.EventBusContract.EVENT;
import static org.apache.james.mailbox.events.EventBusContract.KEY_1;
import static org.apache.james.mailbox.events.EventBusContract.NO_KEYS;
import static org.apache.james.mailbox.events.EventBusContract.ONE_SECOND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

class SeveralRabbitMQEventBusTest {
    private static final TestId.Factory MAILBOX_ID_FACTORY = new TestId.Factory();
    private static final EventSerializer EVENT_SERIALIZER = new EventSerializer(MAILBOX_ID_FACTORY, new TestMessageId.Factory());

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = new RabbitMQExtension();

    private RabbitMQEventBus eventBus1;
    private RabbitMQEventBus eventBus2;

    @BeforeEach
    void setUp() {
        RoutingKeyConverter routingKeyConverter = RoutingKeyConverter.forFactories(new MailboxIdRegistrationKey.Factory(MAILBOX_ID_FACTORY));
        eventBus1 = new RabbitMQEventBus(rabbitMQExtension.getConnectionFactory(), EVENT_SERIALIZER, routingKeyConverter);
        eventBus2 = new RabbitMQEventBus(rabbitMQExtension.getConnectionFactory(), EVENT_SERIALIZER, routingKeyConverter);
        eventBus1.start();
        eventBus2.start();

    }

    @AfterEach
    void tearDown() {
        Mono<Connection> connectionMono = Mono.fromSupplier(rabbitMQExtension.getConnectionFactory()::create).cache();
        Sender sender = RabbitFlux.createSender(new SenderOptions().connectionMono(connectionMono));
        eventBus1.stop();
        eventBus2.stop();
        ALL_GROUPS.stream()
            .map(groupClass -> GroupRegistration.WorkQueueName.of(groupClass).asString())
            .forEach(queueName -> sender.delete(QueueSpecification.queue(queueName)).block());
    }

    private MailboxListener newListener() {
        MailboxListener listener = mock(MailboxListener.class);
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
        return listener;
    }

    @Test
    void crossEventBusRegistrationShouldBeAllowed() {
        MailboxListener mailboxListener = newListener();

        eventBus1.register(mailboxListener, KEY_1);

        eventBus2.dispatch(EVENT, KEY_1).block();

        verify(mailboxListener, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    void unregisteredDistantListenersShouldNotBeNotified() {
        MailboxListener mailboxListener = newListener();

        eventBus1.register(mailboxListener, KEY_1).unregister();

        eventBus2.dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

        verifyZeroInteractions(mailboxListener);
    }

    @Test
    void allRegisteredListenersShouldBeDispatched() {
        MailboxListener mailboxListener1 = newListener();
        MailboxListener mailboxListener2 = newListener();

        eventBus1.register(mailboxListener1, KEY_1);
        eventBus2.register(mailboxListener2, KEY_1);

        eventBus2.dispatch(EVENT, KEY_1).block();

        verify(mailboxListener1, timeout(ONE_SECOND).times(1)).event(any());
        verify(mailboxListener2, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    void groupsDefinedOnlyOnSomeNodesShouldBeNotified() {
        MailboxListener mailboxListener = newListener();

        eventBus1.register(mailboxListener, new EventBusContract.GroupA());

        eventBus2.dispatch(EVENT, NO_KEYS).block();

        verify(mailboxListener, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    void groupListenersShouldBeExecutedOnceInAControlledEnvironment() {
        MailboxListener mailboxListener = newListener();

        eventBus1.register(mailboxListener, new EventBusContract.GroupA());
        eventBus2.register(mailboxListener, new EventBusContract.GroupA());

        eventBus2.dispatch(EVENT, NO_KEYS).block();

        verify(mailboxListener, timeout(ONE_SECOND).times(1)).event(any());
    }

    @Test
    void unregisterShouldStopNotificationForDistantGroups() {
        MailboxListener mailboxListener = newListener();

        eventBus1.register(mailboxListener, new EventBusContract.GroupA()).unregister();

        eventBus2.dispatch(EVENT, NO_KEYS).block();

        verifyZeroInteractions(mailboxListener);
    }
}
