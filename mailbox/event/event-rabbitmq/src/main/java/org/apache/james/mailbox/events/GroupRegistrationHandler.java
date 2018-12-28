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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.MailboxListener;

import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.Sender;

class GroupRegistrationHandler {
    private final EventBusId eventBusId;
    private final Map<Group, GroupRegistration> groupRegistrations;
    private final EventSerializer eventSerializer;
    private final Sender sender;
    private final Mono<Connection> connectionMono;

    GroupRegistrationHandler(EventBusId eventBusId, EventSerializer eventSerializer, Sender sender, Mono<Connection> connectionMono) {
        this.eventBusId = eventBusId;
        this.eventSerializer = eventSerializer;
        this.sender = sender;
        this.connectionMono = connectionMono;
        this.groupRegistrations = new ConcurrentHashMap<>();
    }

    void stop() {
        groupRegistrations.values().forEach(GroupRegistration::unregister);
    }

    Registration register(MailboxListener listener, Group group) {
        return groupRegistrations
            .compute(group, (groupToRegister, oldGroupRegistration) -> {
                if (oldGroupRegistration != null) {
                    throw new GroupAlreadyRegistered(group);
                }
                return newRegistrationGroup(listener, groupToRegister);
            })
            .start();
    }

    private GroupRegistration newRegistrationGroup(MailboxListener listener, Group group) {
        return new GroupRegistration(
            eventBusId,
            connectionMono,
            sender,
            eventSerializer,
            listener,
            group,
            () -> groupRegistrations.remove(group));
    }

    Map<Group, GroupRegistration> getGroupRegistrations() {
        return groupRegistrations;
    }
}
