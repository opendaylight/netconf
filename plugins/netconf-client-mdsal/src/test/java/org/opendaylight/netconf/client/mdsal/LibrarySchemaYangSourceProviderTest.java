/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;

public class LibrarySchemaYangSourceProviderTest {
    private final SourceIdentifier workingSid = new SourceIdentifier("abc");
    private final LibrarySchemaSourceProvider yangLibrarySchemaYangSourceProvider = new LibrarySchemaSourceProvider(
        new RemoteDeviceId("id", new InetSocketAddress("localhost", 22)),
        Map.of(workingSid, LibrarySchemaYangSourceProviderTest.class.getResource("/schemas/config-test-rpc.yang")));

    @Test
    void testGetSource() throws Exception {
        var source = yangLibrarySchemaYangSourceProvider.getSource(workingSid);
        assertThat(source.get().read(), startsWith("module config-test-rpc"));
    }

    @Test
    void testGetSourceFailure() throws InterruptedException, MalformedURLException {
        final var sourceIdentifierURLMap = Map.of(workingSid, new URL("http://non-existing-entity.yang"));
        final var failingYangLibrarySchemaYangSourceProvider = new LibrarySchemaSourceProvider(
            new RemoteDeviceId("id", new InetSocketAddress("localhost", 22)), sourceIdentifierURLMap);

        final var future = failingYangLibrarySchemaYangSourceProvider.getSource(workingSid);
        final var ex = assertThrows(ExecutionException.class, () -> future.get());
        assertInstanceOf(SchemaSourceException.class, ex.getCause());
    }

    @Test
    void testGetSourceNotAvailable() {
        assertThrows(IllegalArgumentException.class,
            () -> yangLibrarySchemaYangSourceProvider.getSource(new SourceIdentifier("aaaaa")));
    }
}
