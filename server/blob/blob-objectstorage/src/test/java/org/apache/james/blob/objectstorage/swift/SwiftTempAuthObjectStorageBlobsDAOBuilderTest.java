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

package org.apache.james.blob.objectstorage.swift;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.UUID;

import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.objectstorage.ContainerName;
import org.apache.james.blob.objectstorage.DockerSwift;
import org.apache.james.blob.objectstorage.DockerSwiftExtension;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAO;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAOBuilder;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAOContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerSwiftExtension.class)
class SwiftTempAuthObjectStorageBlobsDAOBuilderTest implements ObjectStorageBlobsDAOContract {

    private static final TenantName TENANT_NAME = TenantName.of("test");
    private static final UserName USER_NAME = UserName.of("tester");
    private static final Credentials PASSWORD = Credentials.of("testing");
    private static final Identity SWIFT_IDENTITY = Identity.of(TENANT_NAME, USER_NAME);
    private ContainerName containerName;
    private URI endpoint;
    private SwiftTempAuthObjectStorage.Configuration testConfig;

    @BeforeEach
    void setUp(DockerSwift dockerSwift) throws Exception {
        containerName = ContainerName.of(UUID.randomUUID().toString());
        endpoint = dockerSwift.swiftEndpoint();
        testConfig = SwiftTempAuthObjectStorage.configBuilder()
            .endpoint(endpoint)
            .identity(SWIFT_IDENTITY)
            .credentials(PASSWORD)
            .tempAuthHeaderUserName(UserHeaderName.of("X-Storage-User"))
            .tempAuthHeaderPassName(PassHeaderName.of("X-Storage-Pass"))
            .build();
    }

    @Override
    public ContainerName containerName() {
        return containerName;
    }

    @Test
    void containerNameIsMandatoryToBuildBlobsDAO() throws Exception {
        ObjectStorageBlobsDAOBuilder.ReadyToBuild builder = ObjectStorageBlobsDAO
            .builder(testConfig)
            .container(null)
            .blobIdFactory(new HashBlobId.Factory());

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blobIdFactoryIsMandatoryToBuildBlobsDAO() throws Exception {
        ObjectStorageBlobsDAOBuilder.ReadyToBuild builder = ObjectStorageBlobsDAO
            .builder(testConfig)
            .container(containerName)
            .blobIdFactory(null);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void builtBlobsDAOCanStoreAndRetrieve() throws Exception {
        ObjectStorageBlobsDAOBuilder.ReadyToBuild builder = ObjectStorageBlobsDAO
            .builder(testConfig)
            .container(containerName)
            .blobIdFactory(new HashBlobId.Factory());

        assertBlobsDAOCanStoreAndRetrieve(builder);
    }
}