/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

public class LibrarySchemaYangSourceProviderTest {
    private SourceIdentifier workingSid;
    private LibrarySchemaSourceProvider yangLibrarySchemaYangSourceProvider;

    @Before
    public void setUp() throws Exception {
        workingSid = new SourceIdentifier("abc");
        yangLibrarySchemaYangSourceProvider = new LibrarySchemaSourceProvider(
            new RemoteDeviceId("id", new InetSocketAddress("localhost", 22)),
            Map.of(workingSid, getClass().getResource("/schemas/config-test-rpc.yang")));
    }

    @Test
    public void testGetSource() throws Exception {
        var source = yangLibrarySchemaYangSourceProvider.getSource(workingSid);
        final String x = source.get().read();
        assertThat(x, containsString("module config-test-rpc"));
    }

    @Test
    public void testGetSourceFailure() throws InterruptedException, MalformedURLException {
        final var sourceIdentifierURLMap = Map.of(workingSid, new URL("http://non-existing-entity.yang"));
        final var failingYangLibrarySchemaYangSourceProvider = new LibrarySchemaSourceProvider(
            new RemoteDeviceId("id", new InetSocketAddress("localhost", 22)), sourceIdentifierURLMap);

        final var future = failingYangLibrarySchemaYangSourceProvider.getSource(workingSid);
        final var ex = assertThrows(ExecutionException.class, () -> future.get());
        assertThat(ex.getCause(), instanceOf(SchemaSourceException.class));
    }

    @Test
    public void testGetSourceNotAvailable() {
        assertThrows(IllegalArgumentException.class,
            () -> yangLibrarySchemaYangSourceProvider.getSource(new SourceIdentifier("aaaaa")));
    }
}
