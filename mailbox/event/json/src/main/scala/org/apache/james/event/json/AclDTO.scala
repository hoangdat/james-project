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


import org.apache.james.mailbox.acl.{ACLDiff => JavaACLDiff}
import org.apache.james.mailbox.model.{MailboxACL => JavaMailboxACL}

import scala.collection.JavaConverters._

object ACLDiff {
  def fromJava(javaACLDiff: JavaACLDiff): ACLDiff = ACLDiff(
    MailboxACL(javaACLDiff.getOldACL.getEntries.asScala.toMap),
    MailboxACL(javaACLDiff.getNewACL.getEntries.asScala.toMap)
  )
}

case class MailboxACL(entries: Map[JavaMailboxACL.EntryKey, JavaMailboxACL.Rfc4314Rights]) {
  def toJava: JavaMailboxACL = new JavaMailboxACL(entries.asJava)
}

case class ACLDiff(oldACL: MailboxACL, newACL: MailboxACL) {
  def toJava: JavaACLDiff = new JavaACLDiff(oldACL.toJava, newACL.toJava)
}
