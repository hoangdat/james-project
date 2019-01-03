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

import java.util.Set;

import javax.annotation.PreDestroy;

import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;

import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

class RabbitMQEventBus implements EventBus {
    static final String MAILBOX_EVENT = "mailboxEvent";
    static final String MAILBOX_EVENT_EXCHANGE_NAME = MAILBOX_EVENT + "-exchange";
    static final String EMPTY_ROUTING_KEY = "";
    static final String EVENT_BUS_ID = "eventBusId";

    private static final boolean DURABLE = true;
    private static final String DIRECT_EXCHANGE = "direct";

    private final Sender sender;
    private final GroupRegistrationHandler groupRegistrationHandler;
    private final KeyRegistrationHandler keyRegistrationHandler;
    private final EventDispatcher eventDispatcher;
    private final EventBusId eventBusId;
    private final MailboxListenerRegistry mailboxListenerRegistry;

    RabbitMQEventBus(RabbitMQConnectionFactory rabbitMQConnectionFactory, EventSerializer eventSerializer, RoutingKeyConverter routingKeyConverter) {
        this.eventBusId = EventBusId.random();
        this.mailboxListenerRegistry = new MailboxListenerRegistry();
        Mono<Connection> connectionMono = Mono.fromSupplier(rabbitMQConnectionFactory::create).cache();
        this.sender = RabbitFlux.createSender(new SenderOptions().connectionMono(connectionMono));
        this.groupRegistrationHandler = new GroupRegistrationHandler(eventBusId, eventSerializer, sender, connectionMono);
        this.eventDispatcher = new EventDispatcher(eventBusId, eventSerializer, sender, mailboxListenerRegistry, groupRegistrationHandler);
        this.keyRegistrationHandler = new KeyRegistrationHandler(eventBusId, eventSerializer, sender, connectionMono, routingKeyConverter, mailboxListenerRegistry);
    }

    public void start() {
        eventDispatcher.start();
        keyRegistrationHandler.start();
    }

    @PreDestroy
    public void stop() {
        groupRegistrationHandler.stop();
        keyRegistrationHandler.stop();
        sender.close();
    }

    @Override
    public Registration register(MailboxListener listener, RegistrationKey key) {
        return keyRegistrationHandler.register(listener, key);
    }

    @Override
    public Registration register(MailboxListener listener, Group group) {
        return groupRegistrationHandler.register(listener, group);
    }

    @Override
    public Mono<Void> dispatch(Event event, Set<RegistrationKey> keys) {
        if (!event.isNoop()) {
            return eventDispatcher.dispatch(event, keys);
        }
        return Mono.empty();
    }
}
