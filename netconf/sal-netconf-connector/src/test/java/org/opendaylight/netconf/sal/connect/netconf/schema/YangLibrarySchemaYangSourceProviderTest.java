/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
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
        final String x = new String(ByteStreams.toByteArray(source.get().openStream()));
        Assert.assertThat(x, CoreMatchers.containsString("module config-test-rpc"));
    }

    @Test
    public void testGetSourceFailure() throws InterruptedException, MalformedURLException {
        final URL url = new URL("http://non-existing-entity.yang");
        final Map<SourceIdentifier, URL> sourceIdentifierURLMap = Collections.singletonMap(workingSid, url);
        final RemoteDeviceId id = new RemoteDeviceId("id", new InetSocketAddress("localhost", 22));
        final YangLibrarySchemaYangSourceProvider failingYangLibrarySchemaYangSourceProvider =
                new YangLibrarySchemaYangSourceProvider(id, sourceIdentifierURLMap);

        try {
            failingYangLibrarySchemaYangSourceProvider.getSource(workingSid).get();
            fail();
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            assertTrue(cause instanceof SchemaSourceException);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetSourceNotAvailable() throws Exception {
        yangLibrarySchemaYangSourceProvider.getSource(RevisionSourceIdentifier.create("aaaaa"));
    }
}
