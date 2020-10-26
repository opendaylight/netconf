/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.WriterParameters;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfDataServiceConstant.ReadData;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfDataServiceConstant.ReadData.WithDefaults;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ReadDataTransactionUtilTest {

    private static final TestData DATA = new TestData();
    private static final YangInstanceIdentifier.NodeIdentifier NODE_IDENTIFIER = new YangInstanceIdentifier
            .NodeIdentifier(QName.create("ns", "2016-02-28", "container"));

    private RestconfStrategy mdsalStrategy;
    private RestconfStrategy netconfStrategy;
    @Mock
    private NetconfDataTreeService netconfService;
    @Mock
    private DOMTransactionChain transactionChain;
    @Mock
    private InstanceIdentifierContext<ContainerSchemaNode> context;
    @Mock
    private DOMDataTreeReadTransaction read;
    @Mock
    private EffectiveModelContext schemaContext;
    @Mock
    private ContainerSchemaNode containerSchemaNode;
    @Mock
    private LeafSchemaNode containerChildNode;
    private QName containerChildQName;

    @Before
    public void setUp() {
        containerChildQName = QName.create("ns", "2016-02-28", "container-child");

        when(transactionChain.newReadOnlyTransaction()).thenReturn(read);
        when(context.getSchemaContext()).thenReturn(schemaContext);
        when(context.getSchemaNode()).thenReturn(containerSchemaNode);
        when(containerSchemaNode.getQName()).thenReturn(NODE_IDENTIFIER.getNodeType());
        when(containerChildNode.getQName()).thenReturn(containerChildQName);
        when(containerSchemaNode.getDataChildByName(containerChildQName)).thenReturn(containerChildNode);

        DOMDataBroker mockDataBroker = Mockito.mock(DOMDataBroker.class);
        Mockito.doReturn(transactionChain).when(mockDataBroker).createTransactionChain(Mockito.any());
        mdsalStrategy = new MdsalRestconfStrategy(new TransactionChainHandler(mockDataBroker));
        netconfStrategy = new NetconfRestconfStrategy(this.netconfService);
    }

    @Test
    public void readDataConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(this.netconfService).getConfig(DATA.path);
        final String valueOfContent = RestconfDataServiceConstant.ReadData.CONFIG;
        NormalizedNode<?, ?> normalizedNode = readData(valueOfContent, DATA.path, mdsalStrategy);
        assertEquals(DATA.data3, normalizedNode);

        normalizedNode = readData(valueOfContent, DATA.path, netconfStrategy);
        assertEquals(DATA.data3, normalizedNode);
    }

    @Test
    public void readAllHavingOnlyConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(this.netconfService).getConfig(DATA.path);
        doReturn(immediateFluentFuture(Optional.empty())).when(this.netconfService).get(DATA.path);
        final String valueOfContent = RestconfDataServiceConstant.ReadData.ALL;
        NormalizedNode<?, ?> normalizedNode = readData(valueOfContent, DATA.path, mdsalStrategy);
        assertEquals(DATA.data3, normalizedNode);

        normalizedNode = readData(valueOfContent, DATA.path, netconfStrategy);
        assertEquals(DATA.data3, normalizedNode);
    }

    @Test
    public void readAllHavingOnlyNonConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.data2))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path2);
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path2);
        doReturn(immediateFluentFuture(Optional.of(DATA.data2))).when(this.netconfService).get(DATA.path2);
        doReturn(immediateFluentFuture(Optional.empty())).when(this.netconfService).getConfig(DATA.path2);
        final String valueOfContent = RestconfDataServiceConstant.ReadData.ALL;
        NormalizedNode<?, ?> normalizedNode = readData(valueOfContent, DATA.path2, mdsalStrategy);
        assertEquals(DATA.data2, normalizedNode);

        normalizedNode = readData(valueOfContent, DATA.path2, netconfStrategy);
        assertEquals(DATA.data2, normalizedNode);
    }

    @Test
    public void readDataNonConfigTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.data2))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path2);
        doReturn(immediateFluentFuture(Optional.of(DATA.data2))).when(this.netconfService).get(DATA.path2);
        final String valueOfContent = RestconfDataServiceConstant.ReadData.NONCONFIG;
        NormalizedNode<?, ?> normalizedNode = readData(valueOfContent, DATA.path2, mdsalStrategy);
        assertEquals(DATA.data2, normalizedNode);

        normalizedNode = readData(valueOfContent, DATA.path2, netconfStrategy);
        assertEquals(DATA.data2, normalizedNode);
    }

    @Test
    public void readContainerDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data4))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(this.netconfService).getConfig(DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data4))).when(this.netconfService).get(DATA.path);
        final String valueOfContent = RestconfDataServiceConstant.ReadData.ALL;
        final ContainerNode checkingData = Builders
                .containerBuilder()
                .withNodeIdentifier(NODE_IDENTIFIER)
                .withChild(DATA.contentLeaf)
                .withChild(DATA.contentLeaf2)
                .build();
        NormalizedNode<?, ?> normalizedNode = readData(valueOfContent, DATA.path, mdsalStrategy);
        assertEquals(checkingData, normalizedNode);

        normalizedNode = readData(valueOfContent, DATA.path, netconfStrategy);
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readContainerDataConfigNoValueOfContentTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data4))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data3))).when(this.netconfService).getConfig(DATA.path);
        doReturn(immediateFluentFuture(Optional.of(DATA.data4))).when(this.netconfService).get(DATA.path);
        final ContainerNode checkingData = Builders
                .containerBuilder()
                .withNodeIdentifier(NODE_IDENTIFIER)
                .withChild(DATA.contentLeaf)
                .withChild(DATA.contentLeaf2)
                .build();
        NormalizedNode<?, ?> normalizedNode = readData(RestconfDataServiceConstant.ReadData.ALL, DATA.path,
                mdsalStrategy);
        assertEquals(checkingData, normalizedNode);

        normalizedNode = readData(RestconfDataServiceConstant.ReadData.ALL, DATA.path, netconfStrategy);
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.listData))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.listData2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.listData))).when(this.netconfService).get(DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.listData2))).when(this.netconfService).getConfig(DATA.path3);
        final String valueOfContent = RestconfDataServiceConstant.ReadData.ALL;
        final MapNode checkingData = Builders
                .mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("ns", "2016-02-28", "list")))
                .withChild(DATA.checkData)
                .build();
        NormalizedNode<?, ?> normalizedNode = readData(valueOfContent, DATA.path3, mdsalStrategy);
        assertEquals(checkingData, normalizedNode);

        normalizedNode = readData(valueOfContent, DATA.path3, netconfStrategy);
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readOrderedListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedMapNode1))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedMapNode2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedMapNode1))).when(this.netconfService).get(DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedMapNode2))).when(this.netconfService)
                .getConfig(DATA.path3);
        final MapNode expectedData = Builders.orderedMapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(DATA.listQname)).withChild(DATA.checkData)
                .build();
        NormalizedNode<?, ?> normalizedNode = readData(RestconfDataServiceConstant.ReadData.ALL, DATA.path3,
                mdsalStrategy);
        assertEquals(expectedData, normalizedNode);

        normalizedNode = readData(RestconfDataServiceConstant.ReadData.ALL, DATA.path3, netconfStrategy);
        assertEquals(expectedData, normalizedNode);
    }

    @Test
    public void readUnkeyedListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.unkeyedListNode1))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.unkeyedListNode2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.unkeyedListNode1))).when(this.netconfService).get(DATA.path3);
        doReturn(immediateFluentFuture(Optional.of(DATA.unkeyedListNode2))).when(this.netconfService)
                .getConfig(DATA.path3);
        final UnkeyedListNode expectedData = Builders.unkeyedListBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(DATA.listQname))
                .withChild(Builders.unkeyedListEntryBuilder().withNodeIdentifier(
                        new YangInstanceIdentifier.NodeIdentifier(DATA.listQname))
                        .withChild(DATA.unkeyedListEntryNode1.getValue().iterator().next())
                        .withChild(DATA.unkeyedListEntryNode2.getValue().iterator().next()).build()).build();
        NormalizedNode<?, ?> normalizedNode = readData(RestconfDataServiceConstant.ReadData.ALL,
                DATA.path3, mdsalStrategy);
        assertEquals(expectedData, normalizedNode);

        normalizedNode = readData(RestconfDataServiceConstant.ReadData.ALL, DATA.path3, netconfStrategy);
        assertEquals(expectedData, normalizedNode);
    }

    @Test
    public void readLeafListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.leafSetNode1))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.leafSetNodePath);
        doReturn(immediateFluentFuture(Optional.of(DATA.leafSetNode2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.leafSetNodePath);
        doReturn(immediateFluentFuture(Optional.of(DATA.leafSetNode1))).when(this.netconfService)
                .get(DATA.leafSetNodePath);
        doReturn(immediateFluentFuture(Optional.of(DATA.leafSetNode2))).when(this.netconfService)
                .getConfig(DATA.leafSetNodePath);
        final LeafSetNode<String> expectedData = Builders.<String>leafSetBuilder().withNodeIdentifier(
                new YangInstanceIdentifier.NodeIdentifier(DATA.leafListQname)).withValue(
                        ImmutableList.<LeafSetEntryNode<String>>builder().addAll(DATA.leafSetNode1.getValue())
                        .addAll(DATA.leafSetNode2.getValue()).build()).build();
        NormalizedNode<?, ?> normalizedNode = readData(RestconfDataServiceConstant.ReadData.ALL, DATA.leafSetNodePath,
                mdsalStrategy);
        assertEquals(expectedData, normalizedNode);

        normalizedNode = readData(RestconfDataServiceConstant.ReadData.ALL, DATA.leafSetNodePath, netconfStrategy);
        assertEquals(expectedData, normalizedNode);
    }

    @Test
    public void readOrderedLeafListDataAllTest() {
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedLeafSetNode1))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.leafSetNodePath);
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedLeafSetNode2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.leafSetNodePath);
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedLeafSetNode1))).when(this.netconfService)
                .get(DATA.leafSetNodePath);
        doReturn(immediateFluentFuture(Optional.of(DATA.orderedLeafSetNode2))).when(this.netconfService)
                .getConfig(DATA.leafSetNodePath);
        final LeafSetNode<String> expectedData = Builders.<String>orderedLeafSetBuilder().withNodeIdentifier(
                new YangInstanceIdentifier.NodeIdentifier(DATA.leafListQname)).withValue(
                        ImmutableList.<LeafSetEntryNode<String>>builder().addAll(DATA.orderedLeafSetNode1.getValue())
                        .addAll(DATA.orderedLeafSetNode2.getValue()).build()).build();
        NormalizedNode<?, ?> normalizedNode = readData(RestconfDataServiceConstant.ReadData.ALL, DATA.leafSetNodePath,
                mdsalStrategy);
        assertEquals(expectedData, normalizedNode);

        normalizedNode = readData(RestconfDataServiceConstant.ReadData.ALL, DATA.leafSetNodePath, netconfStrategy);
        assertEquals(expectedData, normalizedNode);
    }

    @Test
    public void readDataWrongPathOrNoContentTest() {
        doReturn(immediateFluentFuture(Optional.empty())).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path2);
        doReturn(immediateFluentFuture(Optional.empty())).when(this.netconfService).getConfig(DATA.path2);
        final String valueOfContent = RestconfDataServiceConstant.ReadData.CONFIG;
        NormalizedNode<?, ?> normalizedNode = readData(valueOfContent, DATA.path2, mdsalStrategy);
        assertNull(normalizedNode);

        normalizedNode = readData(valueOfContent, DATA.path2, netconfStrategy);
        assertNull(normalizedNode);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void readDataFailTest() {
        final String valueOfContent = "nonsense";
        NormalizedNode<?, ?> normalizedNode = readData(valueOfContent, null, mdsalStrategy);
        assertNull(normalizedNode);

        normalizedNode = readData(valueOfContent, null, netconfStrategy);
        assertNull(normalizedNode);
    }

    /**
     * Test of parsing default parameters from URI request.
     */
    @Test
    public void parseUriParametersDefaultTest() {
        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();

        // no parameters, default values should be used
        when(uriInfo.getQueryParameters()).thenReturn(parameters);

        final WriterParameters parsedParameters = ReadDataTransactionUtil.parseUriParameters(context, uriInfo);

        assertEquals("Not correctly parsed URI parameter",
                RestconfDataServiceConstant.ReadData.ALL, parsedParameters.getContent());
        assertNull("Not correctly parsed URI parameter",
                parsedParameters.getDepth());
        assertNull("Not correctly parsed URI parameter",
                parsedParameters.getFields());
    }

    /**
     * Test of parsing user defined parameters from URI request.
     */
    @Test
    public void parseUriParametersUserDefinedTest() {
        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();

        final String content = "config";
        final String depth = "10";
        final String fields = containerChildQName.getLocalName();

        parameters.put("content", Collections.singletonList(content));
        parameters.put("depth", Collections.singletonList(depth));
        parameters.put("fields", Collections.singletonList(fields));

        when(uriInfo.getQueryParameters()).thenReturn(parameters);

        final WriterParameters parsedParameters = ReadDataTransactionUtil.parseUriParameters(context, uriInfo);

        // content
        assertEquals("Not correctly parsed URI parameter",
                content, parsedParameters.getContent());

        // depth
        assertNotNull("Not correctly parsed URI parameter",
                parsedParameters.getDepth());
        assertEquals("Not correctly parsed URI parameter",
                depth, parsedParameters.getDepth().toString());

        // fields
        assertNotNull("Not correctly parsed URI parameter",
                parsedParameters.getFields());
        assertEquals("Not correctly parsed URI parameter",
                1, parsedParameters.getFields().size());
        assertEquals("Not correctly parsed URI parameter",
                1, parsedParameters.getFields().get(0).size());
        assertEquals("Not correctly parsed URI parameter",
                containerChildQName, parsedParameters.getFields().get(0).iterator().next());
    }

    /**
     * Negative test of parsing request URI parameters when content parameter has not allowed value.
     */
    @Test
    public void parseUriParametersContentParameterNegativeTest() {
        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();

        parameters.put("content", Collections.singletonList("not-allowed-parameter-value"));
        when(uriInfo.getQueryParameters()).thenReturn(parameters);

        try {
            ReadDataTransactionUtil.parseUriParameters(context, uriInfo);
            fail("Test expected to fail due to not allowed parameter value");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test of parsing request URI parameters when depth parameter has not allowed value.
     */
    @Test
    public void parseUriParametersDepthParameterNegativeTest() {
        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();

        // inserted value is not allowed
        parameters.put("depth", Collections.singletonList("bounded"));
        when(uriInfo.getQueryParameters()).thenReturn(parameters);

        try {
            ReadDataTransactionUtil.parseUriParameters(context, uriInfo);
            fail("Test expected to fail due to not allowed parameter value");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test of parsing request URI parameters when depth parameter has not allowed value (less than minimum).
     */
    @Test
    public void parseUriParametersDepthMinimalParameterNegativeTest() {
        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();

        // inserted value is too low
        parameters.put(
                "depth", Collections.singletonList(String.valueOf(RestconfDataServiceConstant.ReadData.MIN_DEPTH - 1)));
        when(uriInfo.getQueryParameters()).thenReturn(parameters);

        try {
            ReadDataTransactionUtil.parseUriParameters(context, uriInfo);
            fail("Test expected to fail due to not allowed parameter value");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test of parsing request URI parameters when depth parameter has not allowed value (more than maximum).
     */
    @Test
    public void parseUriParametersDepthMaximalParameterNegativeTest() {
        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();

        // inserted value is too high
        parameters.put(
                "depth", Collections.singletonList(String.valueOf(RestconfDataServiceConstant.ReadData.MAX_DEPTH + 1)));
        when(uriInfo.getQueryParameters()).thenReturn(parameters);

        try {
            ReadDataTransactionUtil.parseUriParameters(context, uriInfo);
            fail("Test expected to fail due to not allowed parameter value");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Testing parsing of with-defaults parameter which value doesn't match report-all or report-all-tagged patterns
     * - non-reporting setting.
     */
    @Test
    public void parseUriParametersWithDefaultAndNonTaggedTest() {
        // preparation of input data
        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.put(RestconfDataServiceConstant.ReadData.WITH_DEFAULTS, Collections.singletonList("explicit"));
        when(uriInfo.getQueryParameters()).thenReturn(parameters);

        final WriterParameters writerParameters = ReadDataTransactionUtil.parseUriParameters(context, uriInfo);
        assertSame(WithDefaults.EXPLICIT.value(), writerParameters.getWithDefault());
        assertFalse(writerParameters.isTagged());
    }

    /**
     * Testing parsing of with-defaults parameter which value which is not supported.
     */
    @Test
    public void parseUriParametersWithDefaultInvalidTest() {
        // preparation of input data
        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.put(RestconfDataServiceConstant.ReadData.WITH_DEFAULTS, Collections.singletonList("invalid"));
        when(uriInfo.getQueryParameters()).thenReturn(parameters);

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> ReadDataTransactionUtil.parseUriParameters(context, uriInfo));
        final List<RestconfError> errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorTag.INVALID_VALUE, errors.get(0).getErrorTag());
    }

    /**
     * Testing parsing of with-defaults parameter which value matches 'report-all-tagged' setting - default value should
     * be set to {@code null} and tagged flag should be set to {@code true}.
     */
    @Test
    public void parseUriParametersWithDefaultAndTaggedTest() {
        // preparation of input data
        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.put(RestconfDataServiceConstant.ReadData.WITH_DEFAULTS,
                Collections.singletonList(ReadData.WithDefaults.REPORT_ALL_TAGGED.value()));
        when(uriInfo.getQueryParameters()).thenReturn(parameters);

        final WriterParameters writerParameters = ReadDataTransactionUtil.parseUriParameters(context, uriInfo);
        assertNull(writerParameters.getWithDefault());
        assertTrue(writerParameters.isTagged());
    }

    /**
     * Testing parsing of with-defaults parameter which value matches 'report-all' setting - default value should
     * be set to {@code null} and tagged flag should be set to {@code false}.
     */
    @Test
    public void parseUriParametersWithDefaultAndReportAllTest() {
        // preparation of input data
        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.put(RestconfDataServiceConstant.ReadData.WITH_DEFAULTS,
                Collections.singletonList(ReadData.WithDefaults.REPORT_ALL.value()));
        when(uriInfo.getQueryParameters()).thenReturn(parameters);

        final WriterParameters writerParameters = ReadDataTransactionUtil.parseUriParameters(context, uriInfo);
        assertNull(writerParameters.getWithDefault());
        assertFalse(writerParameters.isTagged());
    }

    /**
     * Test when parameter is present at most once.
     */
    @Test
    public void checkParameterCountTest() {
        ReadDataTransactionUtil.checkParameterCount(List.of("all"), RestconfDataServiceConstant.ReadData.CONTENT);
    }

    /**
     * Test when parameter is present more than once.
     */
    @Test
    public void checkParameterCountNegativeTest() {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> ReadDataTransactionUtil.checkParameterCount(List.of("config", "nonconfig", "all"),
                    RestconfDataServiceConstant.ReadData.CONTENT));
        assertEquals("Error type is not correct", ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
        assertEquals("Error status code is not correct", 400, ex.getErrors().get(0).getErrorTag().getStatusCode());
    }


    /**
     * Test when all parameters are allowed.
     */
    @Test
    public void checkParametersTypesTest() {
        ReadDataTransactionUtil.checkParametersTypes(Set.of("content"),
                RestconfDataServiceConstant.ReadData.CONTENT, RestconfDataServiceConstant.ReadData.DEPTH);
    }

    /**
     * Test when not allowed parameter type is used.
     */
    @Test
    public void checkParametersTypesNegativeTest() {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> ReadDataTransactionUtil.checkParametersTypes(Set.of("not-allowed-parameter"),
                    RestconfDataServiceConstant.ReadData.CONTENT, RestconfDataServiceConstant.ReadData.DEPTH));
        assertEquals("Error type is not correct", ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
        assertEquals("Error status code is not correct", 400, ex.getErrors().get(0).getErrorTag().getStatusCode());
    }

    /**
     * Read specific type of data from data store via transaction.
     *
     * @param valueOfContent type of data to read (config, state, all)
     * @param strategy       {@link RestconfStrategy} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    private @Nullable NormalizedNode<?, ?> readData(final @NonNull String valueOfContent,
            final YangInstanceIdentifier path, final @NonNull RestconfStrategy strategy) {
        return ReadDataTransactionUtil.readData(valueOfContent, path, strategy, null, schemaContext);
    }
}
