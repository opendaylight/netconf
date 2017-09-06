/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.util.Collections;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.WriterParameters;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class ReadDataTransactionUtilTest {

    private static final TestData DATA = new TestData();
    private static final YangInstanceIdentifier.NodeIdentifier NODE_IDENTIFIER = new YangInstanceIdentifier
            .NodeIdentifier(QName.create("ns", "2016-02-28", "container"));

    private TransactionVarsWrapper wrapper;
    @Mock
    private DOMTransactionChain transactionChain;
    @Mock
    private InstanceIdentifierContext<ContainerSchemaNode> context;
    @Mock
    private DOMDataReadOnlyTransaction read;
    @Mock
    private SchemaContext schemaContext;
    @Mock
    private ContainerSchemaNode containerSchemaNode;
    @Mock
    private LeafSchemaNode containerChildNode;
    private QName containerChildQName;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        containerChildQName = QName.create("ns", "2016-02-28", "container-child");

        when(transactionChain.newReadOnlyTransaction()).thenReturn(read);
        when(context.getSchemaContext()).thenReturn(schemaContext);
        when(context.getSchemaNode()).thenReturn(containerSchemaNode);
        when(containerSchemaNode.getQName()).thenReturn(NODE_IDENTIFIER.getNodeType());
        when(containerChildNode.getQName()).thenReturn(containerChildQName);
        when(containerSchemaNode.getDataChildByName(containerChildQName)).thenReturn(containerChildNode);

        wrapper = new TransactionVarsWrapper(this.context, null, this.transactionChain);
    }

    @Test
    public void readDataConfigTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(DATA.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path);
        doReturn(DATA.path).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.CONFIG;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        assertEquals(DATA.data3, normalizedNode);
    }

    @Test
    public void readAllHavingOnlyConfigTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(DATA.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path);
        doReturn(DATA.path).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.ALL;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        assertEquals(DATA.data3, normalizedNode);
    }

    @Test
    public void readAllHavingOnlyNonConfigTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(DATA.data2))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path2);
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path2);
        doReturn(DATA.path2).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.ALL;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        assertEquals(DATA.data2, normalizedNode);
    }

    @Test
    public void readDataNonConfigTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(DATA.data2))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path2);
        doReturn(DATA.path2).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.NONCONFIG;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        assertEquals(DATA.data2, normalizedNode);
    }

    @Test
    public void readContainerDataAllTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(DATA.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path);
        doReturn(Futures.immediateCheckedFuture(Optional.of(DATA.data4))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path);
        doReturn(DATA.path).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.ALL;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        final ContainerNode checkingData = Builders
                .containerBuilder()
                .withNodeIdentifier(NODE_IDENTIFIER)
                .withChild(DATA.contentLeaf)
                .withChild(DATA.contentLeaf2)
                .build();
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readContainerDataConfigNoValueOfContentTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(DATA.data3))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path);
        doReturn(Futures.immediateCheckedFuture(Optional.of(DATA.data4))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path);
        doReturn(DATA.path).when(context).getInstanceIdentifier();
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(
                RestconfDataServiceConstant.ReadData.ALL, wrapper);
        final ContainerNode checkingData = Builders
                .containerBuilder()
                .withNodeIdentifier(NODE_IDENTIFIER)
                .withChild(DATA.contentLeaf)
                .withChild(DATA.contentLeaf2)
                .build();
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readListDataAllTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.of(DATA.listData))).when(read)
                .read(LogicalDatastoreType.OPERATIONAL, DATA.path3);
        doReturn(Futures.immediateCheckedFuture(Optional.of(DATA.listData2))).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path3);
        doReturn(DATA.path3).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.ALL;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        final MapNode checkingData = Builders
                .mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("ns", "2016-02-28", "list")))
                .withChild(DATA.checkData)
                .build();
        assertEquals(checkingData, normalizedNode);
    }

    @Test
    public void readDataWrongPathOrNoContentTest() {
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(read)
                .read(LogicalDatastoreType.CONFIGURATION, DATA.path2);
        doReturn(DATA.path2).when(context).getInstanceIdentifier();
        final String valueOfContent = RestconfDataServiceConstant.ReadData.CONFIG;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(valueOfContent, wrapper);
        assertNull(normalizedNode);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void readDataFailTest() {
        final String valueOfContent = RestconfDataServiceConstant.ReadData.READ_TYPE_TX;
        final NormalizedNode<?, ?> normalizedNode = ReadDataTransactionUtil.readData(
                valueOfContent, wrapper);
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
}
