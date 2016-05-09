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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfStreamsService;
import org.opendaylight.restconf.utils.mapping.RestconfMappingNodeConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.nodes.AbstractImmutableDataContainerAttrNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link RestconfStreamsServiceImpl}
 */
public class RestconfStreamsServiceTest {
    private List<String> expectedStreams = Arrays.asList(new String[] {"stream-1", "stream-2", "stream-3"});

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Mock private SchemaContextHandler contextHandler;
    @Mock private Notificator notificator;

    // service under test
    private RestconfStreamsService streamsService;

    private SchemaContext schemaContext;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        streamsService = new RestconfStreamsServiceImpl(contextHandler);

        Mockito.when(contextHandler.getSchemaContext()).thenReturn(schemaContext);

        // create stream
        Notificator.createListener(YangInstanceIdentifier.EMPTY, expectedStreams.get(0));
        Notificator.createListener(YangInstanceIdentifier.EMPTY, expectedStreams.get(1));
        Notificator.createListener(YangInstanceIdentifier.EMPTY, expectedStreams.get(2));
    }

    /**
     * Positive test to get all available streams supported by the server.
     */
    @Test
    public void getAvailableStreamsTest() throws Exception {
        NormalizedNodeContext nodeContext = streamsService.getAvailableStreams(null);
        assertNotNull("Normalized node context should not be null", nodeContext);

        Collection<MapEntryNode> streamsCollection = (Collection<MapEntryNode>) ((ContainerNode) nodeContext .getData())
                .getValue().iterator().next().getValue();

        List<String> loadedStreams = new ArrayList();
        for (MapEntryNode stream : streamsCollection) {
            Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) stream).getChildren().entrySet().iterator();

            while (mapEntries.hasNext()) {
                Map.Entry e = ((AbstractMap.SimpleImmutableEntry) mapEntries.next());
                String key = ((YangInstanceIdentifier.NodeIdentifier) e.getKey()).getNodeType().getLocalName();

                // fixme other values for stream ?
                switch (key) {
                    case RestconfMappingNodeConstants.NAME :
                        loadedStreams.add((String) ((LeafNode) e.getValue()).getValue());
                        break;
                    case RestconfMappingNodeConstants.DESCRIPTION :
                    case RestconfMappingNodeConstants.REPLAY_SUPPORT :
                    case RestconfMappingNodeConstants.REPLAY_LOG :
                    case RestconfMappingNodeConstants.EVENTS :
                        break;
                    default:
                        throw new Exception("Unknown key");
                }
            }
        }

        verifyStreams(loadedStreams);
    }

    /**
     * Try to get all available streams supported by the server when current <code>SchemaContext</code> is
     * <code>null</code> catching <code>NullPointerException</code>.
     */
    @Test
    public void getAvailableStreamsNullSchemaContextNegativeTest() {
        Mockito.when(contextHandler.getSchemaContext()).thenReturn(null);

        thrown.expect(NullPointerException.class);
        streamsService.getAvailableStreams(null);

        Mockito.when(contextHandler.getSchemaContext()).thenReturn(schemaContext);
    }

    /**
     * Try to get all available streams supported by the server when Restconf module is missing in
     * <code>SchemaContext</code> catching <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void getAvailableStreamsMissingRestconfModuleNegativeTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module does not contain list stream
     * catching <code>RestconfDocumentedException</code>. Error type, error tag and error status code are validated
     * against expected values.
     */
    @Ignore
    @Test
    public void getAvailableStreamsMissingStreamListNegativeTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module does not contain container streams
     * catching <code>RestconfDocumentedException</code>. Error type, error tag and error status code are validated
     * against expected values.
     */
    @Ignore
    @Test
    public void getAvailableStreamsMissingStreamsContainerNegativeTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module contains node stream but it is
     * not of type list. Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Ignore
    @Test
    public void getAvailableStreamsExpectedStreamListNegativeTest() {}

    /**
     * Try to get all available streams supported by the server when Restconf module contains node streams but it is
     * not of type container. Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Ignore
    @Test
    public void getAvailableStreamsExpectedStreamsContainerNegativeTest() {}

    /**
     * Verify loaded streams
     * @param streams Streams to be verified
     */
    private void verifyStreams(List<String> streams) {
        assertEquals("", expectedStreams.size(), streams.size());

        for (String s : expectedStreams) {
            assertTrue("", streams.contains(s));
            streams.remove(s);
        }
    }
}
