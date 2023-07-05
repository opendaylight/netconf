/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.AbstractBodyReaderTest;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.XmlBodyReaderTest;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class XmlBodyReaderMountPointTest extends AbstractBodyReaderTest {
    private static final QNameModule INSTANCE_IDENTIFIER_MODULE_QNAME =  QNameModule.create(
        XMLNamespace.of("instance:identifier:module"), Revision.of("2014-01-17"));

    private static EffectiveModelContext schemaContext;

    private final XmlNormalizedNodeBodyReader xmlBodyReader;

    public XmlBodyReaderMountPointTest() throws Exception {
        super(schemaContext);
        xmlBodyReader = new XmlNormalizedNodeBodyReader(databindProvider, mountPointService);
    }

    @Override
    protected MediaType getMediaType() {
        return new MediaType(MediaType.APPLICATION_XML, null);
    }

    @BeforeClass
    public static void initialization() throws Exception {
        final Collection<File> testFiles = TestRestconfUtils.loadFiles("/instanceidentifier/yang");
        testFiles.addAll(TestRestconfUtils.loadFiles("/invoke-rpc"));
        schemaContext = YangParserTestUtils.parseYangFiles(testFiles);
    }

    @Test
    public void moduleDataTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext
                .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final String uri = "instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont";
        mockBodyReader(uri, xmlBodyReader, false);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderMountPointTest.class.getResourceAsStream("/instanceidentifier/xml/xmldata.xml"));
        checkMountPointNormalizedNodePayload(payload);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, payload);
    }

    @Test
    public void moduleSubContainerDataPutTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext
                .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final String uri = "instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont/cont1";
        mockBodyReader(uri, xmlBodyReader, false);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderMountPointTest.class.getResourceAsStream("/instanceidentifier/xml/xml_sub_container.xml"));
        checkMountPointNormalizedNodePayload(payload);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, payload,
                QName.create(dataSchemaNode.getQName(), "cont1"));
    }

    @Test
    public void moduleSubContainerDataPostTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext
                .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final String uri = "instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont";
        mockBodyReader(uri, xmlBodyReader, true);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderMountPointTest.class.getResourceAsStream("/instanceidentifier/xml/xml_sub_container.xml"));
        checkMountPointNormalizedNodePayload(payload);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, payload);
    }

    @Test
    public void moduleSubContainerDataPostActionTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext
            .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final String uri = "instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont/cont1/reset";
        mockBodyReader(uri, xmlBodyReader, true);
        final NormalizedNodePayload pyaload = xmlBodyReader.readFrom(null,null, null, mediaType, null,
            XmlBodyReaderMountPointTest.class.getResourceAsStream("/instanceidentifier/xml/xml_cont_action.xml"));
        checkMountPointNormalizedNodePayload(pyaload);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, pyaload);
    }

    @Test
    public void rpcModuleInputTest() throws Exception {
        final String uri = "instance-identifier-module:cont/yang-ext:mount/invoke-rpc-module:rpc-test";
        mockBodyReader(uri, xmlBodyReader, true);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderMountPointTest.class.getResourceAsStream("/invoke-rpc/xml/rpc-input.xml"));
        checkNormalizedNodePayload(payload);
        final ContainerNode contNode = (ContainerNode) payload.getData();
        final ContainerNode contDataNode = (ContainerNode) contNode.getChildByArg(
            new NodeIdentifier(QName.create(contNode.name().getNodeType(), "cont")));
        final DataContainerChild leafDataNode = contDataNode.getChildByArg(
            new NodeIdentifier(QName.create(contDataNode.name().getNodeType(), "lf")));
        assertTrue("lf-test".equalsIgnoreCase(leafDataNode.body().toString()));
    }

    private static void checkExpectValueNormalizeNodeContext(final DataSchemaNode dataSchemaNode,
            final NormalizedNodePayload nnContext) {
        checkExpectValueNormalizeNodeContext(dataSchemaNode, nnContext, null);
    }

    private static void checkExpectValueNormalizeNodeContext(final DataSchemaNode dataSchemaNode,
            final NormalizedNodePayload nnContext, final QName qualifiedName) {
        YangInstanceIdentifier dataNodeIdent = YangInstanceIdentifier.of(dataSchemaNode.getQName());
        final DOMMountPoint mountPoint = nnContext.getInstanceIdentifierContext().getMountPoint();
        final DataSchemaNode mountDataSchemaNode = modelContext(mountPoint)
                .getDataChildByName(dataSchemaNode.getQName());
        assertNotNull(mountDataSchemaNode);
        if (qualifiedName != null && dataSchemaNode instanceof DataNodeContainer) {
            final DataSchemaNode child = ((DataNodeContainer) dataSchemaNode).getDataChildByName(qualifiedName);
            dataNodeIdent = YangInstanceIdentifier.builder(dataNodeIdent).node(child.getQName()).build();
            assertEquals(nnContext.getInstanceIdentifierContext().getSchemaNode(), child);
        } else {
            assertEquals(mountDataSchemaNode, dataSchemaNode);
        }
        assertNotNull(NormalizedNodes.findNode(nnContext.getData(), dataNodeIdent));
    }

    /**
     * Test when container with the same name is placed in two modules (foo-module and bar-module). Namespace must be
     * used to distinguish between them to find correct one. Check if container was found not only according to its name
     * but also by correct namespace used in payload.
     */
    @Test
    public void findFooContainerUsingNamespaceTest() throws Exception {
        mockBodyReader("instance-identifier-module:cont/yang-ext:mount", xmlBodyReader, true);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/xml/xmlDataFindFooContainer.xml"));

        // check return value
        checkMountPointNormalizedNodePayload(payload);
        // check if container was found both according to its name and namespace
        final var dataNodeType = payload.getData().name().getNodeType();
        assertEquals("foo-bar-container", dataNodeType.getLocalName());
        assertEquals("foo:module", dataNodeType.getNamespace().toString());
    }

    /**
     * Test when container with the same name is placed in two modules (foo-module and bar-module). Namespace must be
     * used to distinguish between them to find correct one. Check if container was found not only according to its name
     * but also by correct namespace used in payload.
     */
    @Test
    public void findBarContainerUsingNamespaceTest() throws Exception {
        mockBodyReader("instance-identifier-module:cont/yang-ext:mount", xmlBodyReader, true);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/xml/xmlDataFindBarContainer.xml"));

        // check return value
        checkMountPointNormalizedNodePayload(payload);
        // check if container was found both according to its name and namespace
        final var dataNodeType = payload.getData().name().getNodeType();
        assertEquals("foo-bar-container", dataNodeType.getLocalName());
        assertEquals("bar:module", dataNodeType.getNamespace().toString());
    }

    /**
     * Test PUT operation when message root element is not the same as the last element in request URI.
     * PUT operation message should always start with schema node from URI otherwise exception should be
     * thrown.
     */
    @Test
    public void wrongRootElementTest() throws Exception {
        mockBodyReader("instance-identifier-module:cont/yang-ext:mount", xmlBodyReader, false);
        final InputStream inputStream = XmlBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/xml/bug7933.xml");

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> xmlBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        final RestconfError restconfError = ex.getErrors().get(0);
        assertEquals(ErrorType.PROTOCOL, restconfError.getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, restconfError.getErrorTag());
    }
}
