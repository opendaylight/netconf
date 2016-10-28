/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.rest.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.EMPTY;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.Draft17;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.rest.services.api.RestconfStreamsService;
import org.opendaylight.restconf.utils.mapping.RestconfMappingNodeConstants;
import org.opendaylight.restconf.utils.mapping.RestconfMappingStreamConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.nodes.AbstractImmutableDataContainerAttrNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link RestconfStreamsServiceImpl}
 */
public class RestconfStreamsServiceTest {
    private static final List<String> expectedStreams = Arrays.asList("stream-1", "stream-2", "stream-3");

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Mock private SchemaContextHandler contextHandler;
    @Mock private SchemaContext mockSchemaContext;

    // service under test
    private RestconfStreamsService streamsService;

    // schema context with testing Restconf modules
    private SchemaContext schemaContext;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        this.schemaContext = TestRestconfUtils.loadSchemaContext("/modules/restconf-module-testing");
        this.streamsService = new RestconfStreamsServiceImpl(this.contextHandler);
    }

    @BeforeClass
    public static void setupTestStreams() {
        // clean
        Notificator.removeAllListeners();

        // put test streams
        Notificator.createListener(EMPTY, RestconfStreamsServiceTest.expectedStreams.get(0));
        Notificator.createListener(EMPTY, RestconfStreamsServiceTest.expectedStreams.get(1));
        Notificator.createListener(EMPTY, RestconfStreamsServiceTest.expectedStreams.get(2));
    }

    @AfterClass
    public static void removeTestStreams() {
        Notificator.removeAllListeners();
    }

    /**
     * Test of successful initialization of streams service.
     */
    @Test
    public void restconfStreamsServiceImplInitTest() {
        assertNotNull("Streams service should be initialized and not null", this.streamsService);
    }

    /**
     * Positive test to get all available streams supported by the server. Loaded streams are compared to expected
     * streams.
     */
    @Test
    public void getAvailableStreamsTest() throws Exception {
        // prepare conditions - get correct Restconf module
        when(this.contextHandler.get()).thenReturn(this.mockSchemaContext);
        when(this.mockSchemaContext.findModuleByNamespaceAndRevision(Draft17.RestconfModule.IETF_RESTCONF_QNAME
                .getNamespace(), Draft17.RestconfModule.IETF_RESTCONF_QNAME.getRevision()))
                .thenReturn(getTestingRestconfModule("ietf-restconf"));

        // make test
        final NormalizedNodeContext nodeContext = this.streamsService.getAvailableStreams(null);

        // verify loaded streams
        assertNotNull("Normalized node context should not be null", nodeContext);
        verifyStreams(((ContainerNode) nodeContext.getData()).getValue());
    }

    /**
     * Try to get all available streams supported by the server when current <code>SchemaContext</code> is
     * <code>null</code> expecting <code>NullPointerException</code>.
     */
    @Test
    public void getAvailableStreamsNullSchemaContextNegativeTest() {
        // prepare conditions - returned SchemaContext is null
        when(this.contextHandler.get()).thenReturn(null);

        // make test
        this.thrown.expect(NullPointerException.class);
        this.streamsService.getAvailableStreams(null);
    }

    /**
     * Try to get all available streams supported by the server when Restconf module is missing in
     * <code>SchemaContext</code> expecting <code>NullPointerException</code>.
     */
    @Test
    public void getAvailableStreamsMissingRestconfModuleNegativeTest() {
        // prepare conditions - get null Restconf module
        when(this.contextHandler.get()).thenReturn(this.mockSchemaContext);
        when(this.mockSchemaContext.findModuleByNamespaceAndRevision(Draft17.RestconfModule.IETF_RESTCONF_QNAME
                .getNamespace(), Draft17.RestconfModule.IETF_RESTCONF_QNAME.getRevision())).thenReturn(null);

        // make test
        this.thrown.expect(NullPointerException.class);
        this.streamsService.getAvailableStreams(null);
    }

    /**
     * Try to get all available streams supported by the server when Restconf module does not contain list stream
     * catching <code>RestconfDocumentedException</code>. Error type, error tag and error status code are validated
     * against expected values.
     */
    @Test
    public void getAvailableStreamsMissingListStreamNegativeTest() {
        // prepare conditions - get Restconf module with missing list stream
        when(this.contextHandler.get()).thenReturn(this.mockSchemaContext);
        when(this.mockSchemaContext.findModuleByNamespaceAndRevision(Draft17.RestconfModule.IETF_RESTCONF_QNAME
                .getNamespace(), Draft17.RestconfModule.IETF_RESTCONF_QNAME.getRevision()))
                .thenReturn(getTestingRestconfModule("restconf-module-with-missing-list-stream"));

        // make test and verify
        try {
            this.streamsService.getAvailableStreams(null);
            fail("Test is expected to fail due to missing list stream");
        } catch (final RestconfDocumentedException e) {
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
    public void getAvailableStreamsMissingContainerStreamsNegativeTest() {
        // prepare conditions - get Restconf module with missing container streams
        when(this.contextHandler.get()).thenReturn(this.mockSchemaContext);
        when(this.mockSchemaContext.findModuleByNamespaceAndRevision(Draft17.RestconfModule.IETF_RESTCONF_QNAME
                .getNamespace(), Draft17.RestconfModule.IETF_RESTCONF_QNAME.getRevision()))
                .thenReturn(getTestingRestconfModule("restconf-module-with-missing-container-streams"));

        // make test and verify
        try {
            this.streamsService.getAvailableStreams(null);
            fail("Test is expected to fail due to missing container streams");
        } catch (final RestconfDocumentedException e) {
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
    public void getAvailableStreamsIllegalListStreamNegativeTest() {
        // prepare conditions - get Restconf module with illegal list stream
        when(this.contextHandler.get()).thenReturn(this.mockSchemaContext);
        when(this.mockSchemaContext.findModuleByNamespaceAndRevision(Draft17.RestconfModule.IETF_RESTCONF_QNAME
                .getNamespace(), Draft17.RestconfModule.IETF_RESTCONF_QNAME.getRevision()))
                .thenReturn(getTestingRestconfModule("restconf-module-with-illegal-list-stream"));

        // make test
        this.thrown.expect(IllegalStateException.class);
        this.streamsService.getAvailableStreams(null);
    }

    /**
     * Try to get all available streams supported by the server when Restconf module contains node with name 'streams'
     * but it is not of type container. Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getAvailableStreamsIllegalContainerStreamsNegativeTest() {
        // prepare conditions - get Restconf module with illegal container streams
        when(this.contextHandler.get()).thenReturn(this.mockSchemaContext);
        when(this.mockSchemaContext.findModuleByNamespaceAndRevision(Draft17.RestconfModule.IETF_RESTCONF_QNAME
                .getNamespace(), Draft17.RestconfModule.IETF_RESTCONF_QNAME.getRevision()))
                .thenReturn(getTestingRestconfModule("restconf-module-with-illegal-container-streams"));

        // make test
        this.thrown.expect(IllegalStateException.class);
        this.streamsService.getAvailableStreams(null);
    }

    /**
     * Try to get all available streams supported by the server when node 'description' in list stream in Restconf
     * module is not of type leaf. Test is expected to fail with <code>IllegalStateException</code>.
     */
    @Test
    public void getAvailableStreamsIllegalLeafDescriptionNegativeTest() {
        // prepare conditions - get Restconf module with illegal leaf description in list stream
        when(this.contextHandler.get()).thenReturn(this.mockSchemaContext);
        when(this.mockSchemaContext.findModuleByNamespaceAndRevision(Draft17.RestconfModule.IETF_RESTCONF_QNAME
                .getNamespace(), Draft17.RestconfModule.IETF_RESTCONF_QNAME.getRevision()))
                .thenReturn(getTestingRestconfModule("restconf-module-with-illegal-leaf-description"));

        // make test
        this.thrown.expect(IllegalStateException.class);
        this.streamsService.getAvailableStreams(null);
    }

    /**
     * There are multiple testing Restconf modules for different test cases. It is possible to distinguish them by
     * name or by namespace. This method is looking for Restconf test module by its name.
     * @param s Testing Restconf module name
     * @return Restconf module
     */
    private Module getTestingRestconfModule(final String s) {
        return this.schemaContext.findModuleByName(s, Draft17.RestconfModule.IETF_RESTCONF_QNAME.getRevision());
    }

    /**
     * Verify loaded streams
     * @param streams Streams to be verified
     */
    private void verifyStreams(final Collection<DataContainerChild<? extends PathArgument, ?>> streams) {
        assertNotNull("Collection of streams should not be empty", streams);
        assertFalse("Collection of streams should not be empty", Iterables.isEmpty(streams));
        final Iterator<DataContainerChild<? extends PathArgument, ?>> iterator = streams.iterator();

        final List<String> loadedStreams = new ArrayList<>();
        for (final Object stream : (Collection<?>) iterator.next().getValue()) {
            final Iterator mapEntries = ((AbstractImmutableDataContainerAttrNode) stream)
                    .getChildren().entrySet().iterator();

            final List<String> allowedKeys = Lists.newArrayList(
                    RestconfMappingNodeConstants.NAME,
                    RestconfMappingNodeConstants.DESCRIPTION,
                    RestconfMappingNodeConstants.REPLAY_SUPPORT,
                    RestconfMappingNodeConstants.REPLAY_LOG,
                    RestconfMappingNodeConstants.EVENTS);

            while (mapEntries.hasNext()) {
                final Map.Entry e = ((AbstractMap.SimpleImmutableEntry) mapEntries.next());
                final String key = ((NodeIdentifier) e.getKey()).getNodeType().getLocalName();

                assertTrue("Not allowed key", allowedKeys.contains(key));

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
                }
            }
        }

        // sort and compare
        loadedStreams.sort((s1, s2) -> s1.compareTo(s2));
        assertEquals("Returned streams are not as expected", expectedStreams, loadedStreams);
    }
}
