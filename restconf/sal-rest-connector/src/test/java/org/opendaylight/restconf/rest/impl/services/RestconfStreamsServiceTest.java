/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.rest.impl.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfStreamsService;
import org.opendaylight.restconf.utils.mapping.RestconfMappingNodeConstants;
import org.opendaylight.restconf.utils.mapping.RestconfMappingStreamConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.nodes.AbstractImmutableDataContainerAttrNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link RestconfStreamsServiceImpl}
 */
public class RestconfStreamsServiceTest {
    private final List<String> expectedStreams = Arrays.asList(new String[] {"stream-1", "stream-2", "stream-3"});

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Mock private SchemaContextHandler contextHandler;
    @Mock private SchemaContext mockSchemaContext;

    // service under test
    private RestconfStreamsService streamsService;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        streamsService = new RestconfStreamsServiceImpl(contextHandler);

        // create streams
        Notificator.createListener(YangInstanceIdentifier.EMPTY, expectedStreams.get(0));
        Notificator.createListener(YangInstanceIdentifier.EMPTY, expectedStreams.get(1));
        Notificator.createListener(YangInstanceIdentifier.EMPTY, expectedStreams.get(2));
    }

    @Test
    public void restconfStreamsServiceImplInitTest() {
        assertNotNull("Streams service should be initialized and not null", streamsService);
    }

    /**
     * Positive test to get all available streams supported by the server.
     */
    @Test
    public void getAvailableStreamsTest() throws Exception {
        // prepare conditions for successful loading of streams
        when(contextHandler.getSchemaContext()).thenReturn(TestRestconfUtils.loadSchemaContext("/modules"));

        // make test
        final NormalizedNodeContext nodeContext = streamsService.getAvailableStreams(null);
        assertNotNull("Normalized node context should not be null", nodeContext);

        // verify loaded streams
        final Iterator<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> streams = ((ContainerNode)
                nodeContext.getData()).getValue().iterator();

        assertTrue("Collection of streams should not be empty", streams.hasNext());

        final List<String> loadedStreams = new ArrayList<>();
        for (final Object stream : (Collection<?>) streams.next().getValue()) {
            final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) stream)
                    .getChildren().entrySet().iterator();

            while (mapEntries.hasNext()) {
                final Map.Entry e = ((AbstractMap.SimpleImmutableEntry) mapEntries.next());
                final String key = ((YangInstanceIdentifier.NodeIdentifier) e.getKey()).getNodeType().getLocalName();

                switch (key) {
                    case RestconfMappingNodeConstants.NAME :
                        loadedStreams.add((String) ((LeafNode) e.getValue()).getValue());
                        break;
                    case RestconfMappingNodeConstants.DESCRIPTION :
                        assertEquals("Stream description value is not as expected",
                                RestconfMappingStreamConstants.DESCRIPTION, ((LeafNode) e.getValue()).getValue());
                        break;
                    case RestconfMappingNodeConstants.REPLAY_SUPPORT :
                        assertEquals("Stream replay support value is not as expected",
                                RestconfMappingStreamConstants.REPLAY_SUPPORT, ((LeafNode) e.getValue()).getValue());
                        break;
                    case RestconfMappingNodeConstants.REPLAY_LOG :
                        assertEquals("Stream replay log value is not as expected",
                                RestconfMappingStreamConstants.REPLAY_LOG, ((LeafNode) e.getValue()).getValue());
                        break;
                    case RestconfMappingNodeConstants.EVENTS :
                        assertEquals("Stream events value is not as expected",
                                RestconfMappingStreamConstants.EVENTS, ((LeafNode) e.getValue()).getValue());
                        break;
                    default:
                        throw new Exception("Unknown key in Restconf stream definition");
                }
            }
        }

        verifyStreams(loadedStreams);
    }

    /**
     * Try to get all available streams supported by the server when current <code>SchemaContext</code> is
     * <code>null</code> expecting <code>NullPointerException</code>.
     */
    @Test
    public void getAvailableStreamsNullSchemaContextNegativeTest() {
        // prepare conditions - returned SchemaContext is null
        Mockito.when(contextHandler.getSchemaContext()).thenReturn(null);

        // make test
        thrown.expect(NullPointerException.class);
        streamsService.getAvailableStreams(null);
    }

    /**
     * Try to get all available streams supported by the server when Restconf module is missing in
     * <code>SchemaContext</code> expecting <code>NullPointerException</code>.
     */
    @Test
    public void getAvailableStreamsMissingRestconfModuleNegativeTest() {
        // prepare conditions - returned Restconf module is null
        when(contextHandler.getSchemaContext()).thenReturn(mockSchemaContext);
        when(mockSchemaContext.findModuleByNamespaceAndRevision(Draft11.RestconfModule.IETF_RESTCONF_QNAME
                .getNamespace(), Draft11.RestconfModule.IETF_RESTCONF_QNAME.getRevision())).thenReturn(null);

        // make test
        thrown.expect(NullPointerException.class);
        streamsService.getAvailableStreams(null);
    }

    /**
     * Try to get all available streams supported by the server when Restconf module does not contain list stream
     * catching <code>RestconfDocumentedException</code>. Error type, error tag and error status code are validated
     * against expected values.
     */
    @Test
    public void getAvailableStreamsMissingStreamListNegativeTest() throws Exception {
        // prepare conditions - load test SchemaContext
        when(contextHandler.getSchemaContext()).thenReturn(TestRestconfUtils.loadSchemaContext
                ("/modules/restconf-streams-testing/missing-list-stream"));

        // make test
        try {
            streamsService.getAvailableStreams(null);
            fail("Test is expected to fail due to missing list stream");
        } catch (RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    RestconfError.ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to get all available streams supported by the server when Restconf module does not contain container streams
     * catching <code>RestconfDocumentedException</code>. Error type, error tag and error status code are validated
     * against expected values.
     */
    @Test
    public void getAvailableStreamsMissingStreamsContainerNegativeTest() throws Exception {
        // prepare conditions - load SchemaContext
        when(contextHandler.getSchemaContext()).thenReturn(TestRestconfUtils.loadSchemaContext
                ("/modules/restconf-streams-testing/missing-container-streams"));

        // make test
        try {
            streamsService.getAvailableStreams(null);
            fail("Test is expected to fail due to missing container streams");
        } catch (RestconfDocumentedException e) {
            assertEquals("Error type is not correct",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct",
                    RestconfError.ErrorTag.DATA_MISSING, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct",
                    404, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Try to get all available streams supported by the server when Restconf module contains node with name 'stream'
     * but it is not of type list. Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getAvailableStreamsIllegalStreamListNegativeTest() throws Exception {
        // prepare conditions - load SchemaContext
        when(contextHandler.getSchemaContext()).thenReturn(TestRestconfUtils.loadSchemaContext
                ("/modules/restconf-streams-testing/illegal-list-stream"));

        // make test
        thrown.expect(IllegalStateException.class);
        streamsService.getAvailableStreams(null);
    }

    /**
     * Try to get all available streams supported by the server when Restconf module contains node with name 'streams'
     * but it is not of type container. Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getAvailableStreamsIllegalStreamsContainerNegativeTest() throws Exception {
        // prepare conditions - load SchemaContext
        when(contextHandler.getSchemaContext()).thenReturn(TestRestconfUtils.loadSchemaContext
                ("/modules/restconf-streams-testing/illegal-container-streams"));

        // make test
        thrown.expect(IllegalStateException.class);
        streamsService.getAvailableStreams(null);
    }

    /**
     * Verify loaded streams. Compare loaded stream names to expected stream names.
     * @param streams Streams to be verified
     */
    private void verifyStreams(final List<String> streams) {
        assertEquals("Loaded number of streams is not correct", expectedStreams.size(), streams.size());

        for (final String s : expectedStreams) {
            assertTrue("Stream " + s + " should be found in restconf module", streams.contains(s));
            streams.remove(s);
        }
    }
}
