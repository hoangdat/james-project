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

import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

class RabbitMQEventBusTest implements EventBusContract {

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = new RabbitMQExtension();

    private RabbitMQEventBus eventBus;
    private Sender sender;

    @BeforeEach
    void setUp() {
        RabbitMQConnectionFactory connectionFactory = rabbitMQExtension.getConnectionFactory();
        Mono<Connection> connectionMono = Mono.fromSupplier(connectionFactory::create).cache();

        TestId.Factory mailboxIdFactory = new TestId.Factory();
        EventSerializer eventSerializer = new EventSerializer(mailboxIdFactory, new TestMessageId.Factory());
        RoutingKeyConverter routingKeyConverter = RoutingKeyConverter.forFactories(new MailboxIdRegistrationKey.Factory(mailboxIdFactory));

        eventBus = new RabbitMQEventBus(connectionFactory, eventSerializer, routingKeyConverter);
        eventBus.start();
        sender = RabbitFlux.createSender(new SenderOptions().connectionMono(connectionMono));
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
        ALL_GROUPS.stream()
            .map(groupClass -> GroupRegistration.WorkQueueName.of(groupClass).asString())
            .forEach(queueName -> sender.delete(QueueSpecification.queue(queueName)).block());
    }

    @Override
    public EventBus eventBus() {
        return eventBus;
    }

    @Override
    @Test
    @Disabled("This test is failing by design as the different registration keys are handled by distinct messages")
    public void dispatchShouldCallListenerOnceWhenSeveralKeysMatching() {

    }
}
