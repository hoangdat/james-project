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

package org.apache.james;

import java.io.IOException;

import org.apache.james.backend.rabbitmq.DockerRabbitMQSingleton;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestESMetricReporterModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.TestRabbitMQModule;
import org.apache.james.modules.TestSwiftBlobStoreModule;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Module;

public class CassandraRabbitMQSwiftJmapTestRule implements TestRule {

    private static final int LIMIT_TO_10_MESSAGES = 10;
    private final TemporaryFolder temporaryFolder;

    public static CassandraRabbitMQSwiftJmapTestRule defaultTestRule() {
        return new CassandraRabbitMQSwiftJmapTestRule(new EmbeddedElasticSearchRule());
    }

    private final GuiceModuleTestRule guiceModuleTestRule;

    public CassandraRabbitMQSwiftJmapTestRule(GuiceModuleTestRule... guiceModuleTestRule) {
        TempFilesystemTestRule tempFilesystemTestRule = new TempFilesystemTestRule();
        this.temporaryFolder = tempFilesystemTestRule.getTemporaryFolder();
        this.guiceModuleTestRule =
                AggregateGuiceModuleTestRule
                    .of(guiceModuleTestRule)
                    .aggregate(tempFilesystemTestRule);
    }

    public GuiceJamesServer jmapServer(Module... additionals) throws IOException {
        Configuration configuration = Configuration.builder()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .build();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(CassandraRabbitMQJamesServerMain.MODULES)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestRabbitMQModule(DockerRabbitMQSingleton.SINGLETON))
            .overrideWith(new TestSwiftBlobStoreModule())
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
            .overrideWith(new TestESMetricReporterModule())
            .overrideWith(guiceModuleTestRule.getModule())
            .overrideWith(additionals);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return guiceModuleTestRule.apply(base, description);
    }

    public void await() {
        guiceModuleTestRule.await();
    }
}
