/** **************************************************************
  * Licensed to the Apache Software Foundation (ASF) under one   *
  * or more contributor license agreements.  See the NOTICE file *
  * distributed with this work for additional information        *
  * regarding copyright ownership.  The ASF licenses this file   *
  * to you under the Apache License, Version 2.0 (the            *
  * "License"); you may not use this file except in compliance   *
  * with the License.  You may obtain a copy of the License at   *
  * *
  * http://www.apache.org/licenses/LICENSE-2.0                 *
  * *
  * Unless required by applicable law or agreed to in writing,   *
  * software distributed under the License is distributed on an  *
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
  * KIND, either express or implied.  See the License for the    *
  * specific language governing permissions and limitations      *
  * under the License.                                           *
  * ***************************************************************/

package org.apache.james.event.json

import java.time.Instant
import java.util.Optional

import julienrf.json.derived
import org.apache.james.core.quota.{QuotaCount, QuotaSize, QuotaValue}
import org.apache.james.core.{Domain, User}
import org.apache.james.mailbox.MailboxListener.{MailboxACLUpdated => JavaMailboxACLUpdated, MailboxAdded => JavaMailboxAdded, MailboxDeletion => JavaMailboxDeletion, MailboxRenamed => JavaMailboxRenamed, QuotaUsageUpdatedEvent => JavaQuotaUsageUpdatedEvent}
import org.apache.james.mailbox.MailboxSession.SessionId
import org.apache.james.mailbox.model.{MailboxId, QuotaRoot, MailboxACL => JavaMailboxACL, Quota => JavaQuota}
import org.apache.james.mailbox.{Event => JavaEvent}
import play.api.libs.json.{JsError, JsNull, JsNumber, JsObject, JsResult, JsString, JsSuccess, Json, OFormat, Reads, Writes}

import scala.collection.JavaConverters._

private sealed trait Event {
  def toJava: JavaEvent
}

private object DTO {

  case class QuotaUsageUpdatedEvent(user: User, quotaRoot: QuotaRoot, countQuota: Quota[QuotaCount],
                                    sizeQuota: Quota[QuotaSize], time: Instant) extends Event {
    override def toJava: JavaEvent = new JavaQuotaUsageUpdatedEvent(user, quotaRoot, countQuota.toJava, sizeQuota.toJava, time)
  }

  case class MailboxAdded(mailboxPath: MailboxPath, mailboxId: MailboxId, user: User, sessionId: SessionId) extends Event {
    override def toJava: JavaEvent = new JavaMailboxAdded(sessionId, user, mailboxPath.toJava, mailboxId)
  }

  case class MailboxRenamed(sessionId: SessionId, user: User, path: MailboxPath, mailboxId: MailboxId, newPath: MailboxPath) extends Event {
    override def toJava: JavaEvent = new JavaMailboxRenamed(sessionId, user, path.toJava, mailboxId, newPath.toJava)
  }

  case class MailboxDeletion(sessionId: SessionId, user: User, path: MailboxPath, quotaRoot: QuotaRoot,
                             deletedMessageCount: QuotaCount, totalDeletedSize: QuotaSize, mailboxId: MailboxId) extends Event {
    override def toJava: JavaEvent = new JavaMailboxDeletion(sessionId, user, path.toJava, quotaRoot, deletedMessageCount,
      totalDeletedSize,
      mailboxId)
  }

  case class MailboxACLUpdated(sessionId: SessionId, user: User, mailboxPath: MailboxPath, aclDiff: ACLDiff, mailboxId: MailboxId) extends Event {
    override def toJava: JavaEvent = new JavaMailboxACLUpdated(sessionId, user, mailboxPath.toJava, aclDiff.toJava, mailboxId)
  }
}

private object ScalaConverter {
  private def toScala[T <: QuotaValue[T]](java: JavaQuota[T]): Quota[T] = Quota(
    used = java.getUsed,
    limit = java.getLimit,
    limits = java.getLimitByScope.asScala.toMap)

  private def toScala(event: JavaQuotaUsageUpdatedEvent): DTO.QuotaUsageUpdatedEvent = DTO.QuotaUsageUpdatedEvent(
    user = event.getUser,
    quotaRoot = event.getQuotaRoot,
    countQuota = toScala(event.getCountQuota),
    sizeQuota = toScala(event.getSizeQuota),
    time = event.getInstant)

  private def toScala(event: JavaMailboxAdded): DTO.MailboxAdded = DTO.MailboxAdded(
    mailboxPath = MailboxPath.fromJava(event.getMailboxPath),
    mailboxId = event.getMailboxId,
    user = event.getUser,
    sessionId = event.getSessionId)

  private def toScala(event: JavaMailboxRenamed): DTO.MailboxRenamed = DTO.MailboxRenamed(
    sessionId = event.getSessionId,
    user = event.getUser,
    path = MailboxPath.fromJava(event.getMailboxPath),
    mailboxId = event.getMailboxId,
    newPath = MailboxPath.fromJava(event.getNewPath))

  private def toScala(event: JavaMailboxDeletion): DTO.MailboxDeletion = DTO.MailboxDeletion(
    sessionId = event.getSessionId,
    user = event.getUser,
    quotaRoot = event.getQuotaRoot,
    path = MailboxPath.fromJava(event.getMailboxPath),
    deletedMessageCount = event.getDeletedMessageCount,
    totalDeletedSize = event.getTotalDeletedSize,
    mailboxId = event.getMailboxId)

  private def toScala(event: JavaMailboxACLUpdated): DTO.MailboxACLUpdated = DTO.MailboxACLUpdated(
    sessionId = event.getSessionId,
    user = event.getUser,
    mailboxPath = MailboxPath.fromJava(event.getMailboxPath),
    aclDiff = ACLDiff.fromJava(event.getAclDiff),
    mailboxId = event.getMailboxId)

  def toScala(javaEvent: JavaEvent): Event = javaEvent match {
    case e: JavaQuotaUsageUpdatedEvent => toScala(e)
    case e: JavaMailboxAdded => toScala(e)
    case e: JavaMailboxRenamed => toScala(e)
    case e: JavaMailboxDeletion => toScala(e)
    case e: JavaMailboxACLUpdated => toScala(e)
    case _ => throw new RuntimeException("no Scala convertion known")
  }
}

private class JsonSerialize(mailboxIdFactory: MailboxId.Factory) {
  implicit val userWriters: Writes[User] = (user: User) => JsString(user.asString)
  implicit val quotaRootWrites: Writes[QuotaRoot] = quotaRoot => JsString(quotaRoot.getValue)
  implicit val quotaValueWrites: Writes[QuotaValue[_]] = value => if (value.isUnlimited) JsNull else JsNumber(value.asLong())
  implicit val quotaScopeWrites: Writes[JavaQuota.Scope] = value => JsString(value.name)
  implicit val quotaCountWrites: Writes[Quota[QuotaCount]] = Json.writes[Quota[QuotaCount]]
  implicit val quotaSizeWrites: Writes[Quota[QuotaSize]] = Json.writes[Quota[QuotaSize]]
  implicit val mailboxPathWrites: Writes[MailboxPath] = Json.writes[MailboxPath]
  implicit val mailboxIdWrites: Writes[MailboxId] = value => JsString(value.serialize())
  implicit val sessionIdWrites: Writes[SessionId] = value => JsNumber(value.getValue)
  implicit val aclEntryKeyWrites: Writes[JavaMailboxACL.EntryKey] = value => JsString(value.serialize())
  implicit val aclRightsWrites: Writes[JavaMailboxACL.Rfc4314Rights] = value => JsString(value.serialize())
  implicit val mailboxAclWrites: Writes[MailboxACL] = Json.writes[MailboxACL]
  implicit val aclDiffWrites: Writes[ACLDiff] = Json.writes[ACLDiff]

  implicit val aclEntryKeyReads: Reads[JavaMailboxACL.EntryKey] = {
    case JsString(keyAsString) => JsSuccess(JavaMailboxACL.EntryKey.deserialize(keyAsString))
    case _ => JsError()
  }
  implicit val aclRightsReads: Reads[JavaMailboxACL.Rfc4314Rights] = {
    case JsString(rightsAsString) => JsSuccess(JavaMailboxACL.Rfc4314Rights.deserialize(rightsAsString))
    case _ => JsError()
  }
  implicit val userReads: Reads[User] = {
    case JsString(userAsString) => JsSuccess(User.fromUsername(userAsString))
    case _ => JsError()
  }
  implicit val mailboxIdReads: Reads[MailboxId] = {
    case JsString(serializedMailboxId) => JsSuccess(mailboxIdFactory.fromString(serializedMailboxId))
    case _ => JsError()
  }
  implicit val sessionIdReads: Reads[SessionId] = {
    case JsNumber(id) => JsSuccess(SessionId.of(id.longValue()))
    case _ => JsError()
  }
  implicit val quotaRootReads: Reads[QuotaRoot] = {
    case JsString(quotaRoot) => JsSuccess(QuotaRoot.quotaRoot(quotaRoot, Optional.empty[Domain]))
    case _ => JsError()
  }
  implicit val quotaCountReads: Reads[QuotaCount] = {
    case JsNumber(count) => JsSuccess(QuotaCount.count(count.toLong))
    case JsNull => JsSuccess(QuotaCount.unlimited())
    case _ => JsError()
  }
  implicit val quotaSizeReads: Reads[QuotaSize] = {
    case JsNumber(size) => JsSuccess(QuotaSize.size(size.toLong))
    case JsNull => JsSuccess(QuotaSize.unlimited())
    case _ => JsError()
  }
  implicit val quotaScopeReads: Reads[JavaQuota.Scope] = {
    case JsString(value) => JsSuccess(JavaQuota.Scope.valueOf(value))
    case _ => JsError()
  }

  implicit def scopeMapReads[V](implicit vr: Reads[V]): Reads[Map[JavaQuota.Scope, V]] =
    Reads.mapReads[JavaQuota.Scope, V] { str =>
      Json.fromJson[JavaQuota.Scope](JsString(str))
    }

  implicit def scopeMapWrite[V](implicit vr: Writes[V]): Writes[Map[JavaQuota.Scope, V]] =
    (m: Map[JavaQuota.Scope, V]) => {
      JsObject(m.map { case (k, v) => (k.toString, vr.writes(v)) }.toSeq)
    }

  implicit def scopeMapReadsACL[V](implicit vr: Reads[V]): Reads[Map[JavaMailboxACL.EntryKey, V]] =
    Reads.mapReads[JavaMailboxACL.EntryKey, V] { str =>
      Json.fromJson[JavaMailboxACL.EntryKey](JsString(str))
    }

  implicit def scopeMapWriteACL[V](implicit vr: Writes[V]): Writes[Map[JavaMailboxACL.EntryKey, V]] =
    (m: Map[JavaMailboxACL.EntryKey, V]) => {
      JsObject(m.map { case (k, v) => (k.toString, vr.writes(v)) }.toSeq)
    }

  implicit val quotaCReads: Reads[Quota[QuotaCount]] = Json.reads[Quota[QuotaCount]]
  implicit val quotaSReads: Reads[Quota[QuotaSize]] = Json.reads[Quota[QuotaSize]]
  implicit val mailboxPathReads: Reads[MailboxPath] = Json.reads[MailboxPath]
  implicit val mailboxAclReads: Reads[MailboxACL] = Json.reads[MailboxACL]
  implicit val aclDiffReads: Reads[ACLDiff] = Json.reads[ACLDiff]

  implicit val eventOFormat: OFormat[Event] = derived.oformat()

  def toJson(event: Event): String = Json.toJson(event).toString()

  def fromJson(json: String): JsResult[Event] = Json.fromJson[Event](Json.parse(json))
}

class EventSerializer(mailboxIdFactory: MailboxId.Factory) {
  def toJson(event: JavaEvent): String = new JsonSerialize(mailboxIdFactory).toJson(ScalaConverter.toScala(event))

  def fromJson(json: String): JsResult[JavaEvent] = {
    new JsonSerialize(mailboxIdFactory)
      .fromJson(json)
      .map(event => event.toJava)
  }
}
