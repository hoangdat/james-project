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

package org.apache.james.vault;

import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class DeletedMessageVaultHook implements PreDeletionHook {

    private final MailboxSession session;
    private final DeletedMessageVault deletedMessageVault;
    private final DeletedMessageConverter deletedMessageConverter;
    private final MailboxSessionMapperFactory mapperFactory;

    DeletedMessageVaultHook(SessionProvider sessionProvider,
                            DeletedMessageVault deletedMessageVault,
                            DeletedMessageConverter deletedMessageConverter,
                            MailboxSessionMapperFactory mapperFactory) {
        this.session = sessionProvider.createSystemSession(getClass().getName());
        this.deletedMessageVault = deletedMessageVault;
        this.deletedMessageConverter = deletedMessageConverter;
        this.mapperFactory = mapperFactory;
    }

    @Override
    public Publisher<Void> notifyDelete(DeleteOperation deleteOperation) {
        Preconditions.checkNotNull(deleteOperation);

        return groupMetadataByOwnerAndMessageId(deleteOperation)
            .flatMap(Throwing.<DeletedMessageMetadata, Mono<Void>>function(entry -> appendToTheVault(entry)).sneakyThrow())
            .then();
    }

    private Mono<Void> appendToTheVault(DeletedMessageMetadata deletedMessageMetadata) throws MailboxException {
        Optional<MailboxMessage> maybeMailboxMessage = mapperFactory.getMessageIdMapper(session)
            .find(ImmutableList.of(deletedMessageMetadata.getMessageId()), MessageMapper.FetchType.Full).stream()
            .findFirst();

        return maybeMailboxMessage.map(Throwing.function(mailboxMessage -> Pair.of(mailboxMessage,
                deletedMessageConverter.convert(deletedMessageMetadata, mailboxMessage))))
            .map(Throwing.function(pairs -> Mono.from(deletedMessageVault
                .append(pairs.getRight().getOwner(), pairs.getRight(), pairs.getLeft().getFullContent()))))
            .orElse(Mono.empty());
    }

    private Flux<DeletedMessageMetadata> groupMetadataByOwnerAndMessageId(DeleteOperation deleteOperation) {
        return Flux.fromIterable(deleteOperation.getDeletionMetadataList())
            .publishOn(Schedulers.elastic())
            .map(Throwing.function(this::toMetadataWithOwner))
            .groupBy(this::createGroupingKeyMapper)
            .flatMap(this::toDeletedMessageMetadata);
    }

    private Mono<DeletedMessageMetadata> toDeletedMessageMetadata(GroupedFlux<Pair<MessageId, User>, DeletedMessageMetadata> groupedFlux) {
        return groupedFlux.reduce(DeletedMessageMetadata::combineWith);
    }

    private DeletedMessageMetadata toMetadataWithOwner(MetadataWithMailboxId metadataWithMailboxId) throws MailboxException {
        User owner = User.fromUsername(mapperFactory.getMailboxMapper(session)
            .findMailboxById(metadataWithMailboxId.getMailboxId())
            .getUser());

        return new DeletedMessageMetadata(metadataWithMailboxId.getMessageMetaData().getMessageId(), owner, ImmutableList.of(metadataWithMailboxId.getMailboxId()));
    }

    private Pair<MessageId, User> createGroupingKeyMapper(DeletedMessageMetadata deletedMessageMetadata) {
        return Pair.of(deletedMessageMetadata.getMessageId(), deletedMessageMetadata.getOwner());
    }
}