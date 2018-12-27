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

import static org.apache.james.backend.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backend.rabbitmq.Constants.DURABLE;
import static org.apache.james.backend.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT_EXCHANGE_NAME;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

class EventDispatcher {
    private final EventBusId eventBusId;
    private final EventSerializer eventSerializer;
    private final Sender sender;
    private final MailboxListenerRegistry mailboxListenerRegistry;

    EventDispatcher(EventBusId eventBusId, EventSerializer eventSerializer, Sender sender, MailboxListenerRegistry mailboxListenerRegistry) {
        this.eventBusId = eventBusId;
        this.eventSerializer = eventSerializer;
        this.sender = sender;
        this.mailboxListenerRegistry = mailboxListenerRegistry;
    }

    void start() {
        sender.declareExchange(ExchangeSpecification.exchange(MAILBOX_EVENT_EXCHANGE_NAME)
            .durable(DURABLE)
            .type(DIRECT_EXCHANGE))
            .block();
    }

    Mono<Void> dispatch(Event event, Set<RegistrationKey> keys) {
        Mono<Void> localListenerDelivery = Flux.fromIterable(keys)
            .subscribeOn(Schedulers.elastic())
            .flatMap(mailboxListenerRegistry::getLocalMailboxListeners)
            .filter(mailboxListener -> mailboxListener.getExecutionMode() == MailboxListener.ExecutionMode.SYNCHRONOUS)
            .flatMap(mailboxListener -> Mono.fromRunnable(() -> mailboxListener.event(event)))
            .then().cache();

        Mono<byte[]> serializedEvent = Mono.just(event)
            .publishOn(Schedulers.parallel())
            .map(this::serializeEvent)
            .cache();

        Mono<Void> distantDispatchMono = doDispatch(serializedEvent, keys);

        Mono<Void> dispatchMono = Flux.concat(localListenerDelivery, distantDispatchMono).then().cache();
        dispatchMono.subscribe();

        return dispatchMono;
    }

    private Mono<Void> doDispatch(Mono<byte[]> serializedEvent, Set<RegistrationKey> keys) {
        Flux<String> routingKeys = Flux.concat(
            Mono.just(EMPTY_ROUTING_KEY),
            Flux.fromIterable(keys)
                .map(RoutingKeyConverter::toRoutingKey));

        Flux<OutboundMessage> outboundMessages = routingKeys
            .flatMap(routingKey -> serializedEvent
                .map(payload -> generateMessage(routingKey, payload)));

        return sender.send(outboundMessages);
    }

    private byte[] serializeEvent(Event event) {
        return eventSerializer.toJson(event).getBytes(StandardCharsets.UTF_8);
    }

    private OutboundMessage generateMessage(String routingKey, byte[] payload) {
        Map<String, Object> headers = ImmutableMap
            .of(RabbitMQEventBus.EVENT_BUS_ID, eventBusId.getId().toString());
        AMQP.BasicProperties.Builder propertiesBuilder = new AMQP.BasicProperties.Builder();

        return new OutboundMessage(MAILBOX_EVENT_EXCHANGE_NAME, routingKey, propertiesBuilder
            .headers(headers).build(), payload);
    }
}
