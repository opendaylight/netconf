/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.nativ.netconf.communicator.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;

public class YangLibrarySchemaYangSourceProviderTest {
    private SourceIdentifier workingSid;
    private YangLibrarySchemaYangSourceProvider yangLibrarySchemaYangSourceProvider;

    @Before
    public void setUp() throws Exception {
        final URL url = getClass().getResource("/schemas/config-test-rpc.yang");
        workingSid = RevisionSourceIdentifier.create("abc", Optional.empty());
        final Map<SourceIdentifier, URL> sourceIdentifierURLMap = Collections.singletonMap(workingSid, url);
        final RemoteDeviceId id = new RemoteDeviceId("id", new InetSocketAddress("localhost", 22));
        yangLibrarySchemaYangSourceProvider = new YangLibrarySchemaYangSourceProvider(id, sourceIdentifierURLMap);
    }

    @Test
    public void testGetSource() throws Exception {
        ListenableFuture<? extends YangTextSchemaSource> source = yangLibrarySchemaYangSourceProvider
                .getSource(workingSid);

        final String x = source.get().asCharSource(StandardCharsets.UTF_8).read();
        assertThat(x, containsString("module config-test-rpc"));
    }

    @Test
    public void testGetSourceFailure() throws InterruptedException, MalformedURLException {
        final URL url = new URL("http://non-existing-entity.yang");
        final Map<SourceIdentifier, URL> sourceIdentifierURLMap = Collections.singletonMap(workingSid, url);
        final RemoteDeviceId id = new RemoteDeviceId("id", new InetSocketAddress("localhost", 22));
        final YangLibrarySchemaYangSourceProvider failingYangLibrarySchemaYangSourceProvider =
                new YangLibrarySchemaYangSourceProvider(id, sourceIdentifierURLMap);

        final ListenableFuture<?> future = failingYangLibrarySchemaYangSourceProvider.getSource(workingSid);
        final ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get());
        assertThat(ex.getCause(), instanceOf(SchemaSourceException.class));
    }

    @Test
    public void testGetSourceNotAvailable() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> yangLibrarySchemaYangSourceProvider.getSource(RevisionSourceIdentifier.create("aaaaa")));
    }
}
