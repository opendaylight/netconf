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
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
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
    private static final MediaType MEDIA_TYPE = new MediaType(MediaType.APPLICATION_XML, null);

    private static EffectiveModelContext schemaContext;

    private final XmlNormalizedNodeBodyReader xmlBodyReader;

    public XmlBodyReaderMountPointTest() {
        super(schemaContext);
        xmlBodyReader = new XmlNormalizedNodeBodyReader(databindProvider, mountPointService);
    }

    @BeforeClass
    public static void initialization() throws Exception {
        final var testFiles = loadFiles("/instanceidentifier/yang");
        testFiles.addAll(loadFiles("/invoke-rpc"));
        schemaContext = YangParserTestUtils.parseYangFiles(testFiles);
    }

    @Test
    public void moduleSubContainerDataPostTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext
                .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final String uri = "instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont";
        mockPostBodyReader(uri, xmlBodyReader);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, MEDIA_TYPE, null,
            XmlBodyReaderMountPointTest.class.getResourceAsStream("/instanceidentifier/xml/xml_sub_container.xml"));
        checkMountPointNormalizedNodePayload(payload);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, payload);
    }

    @Test
    public void moduleSubContainerDataPostActionTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext
            .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final String uri = "instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont/cont1/reset";
        mockPostBodyReader(uri, xmlBodyReader);
        final NormalizedNodePayload pyaload = xmlBodyReader.readFrom(null,null, null, MEDIA_TYPE, null,
            XmlBodyReaderMountPointTest.class.getResourceAsStream("/instanceidentifier/xml/xml_cont_action.xml"));
        checkMountPointNormalizedNodePayload(pyaload);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, pyaload);
    }

    @Test
    public void rpcModuleInputTest() throws Exception {
        final String uri = "instance-identifier-module:cont/yang-ext:mount/invoke-rpc-module:rpc-test";
        mockPostBodyReader(uri, xmlBodyReader);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, MEDIA_TYPE, null,
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
        mockPostBodyReader("instance-identifier-module:cont/yang-ext:mount", xmlBodyReader);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, MEDIA_TYPE, null,
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
        mockPostBodyReader("instance-identifier-module:cont/yang-ext:mount", xmlBodyReader);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, MEDIA_TYPE, null,
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/xml/xmlDataFindBarContainer.xml"));

        // check return value
        checkMountPointNormalizedNodePayload(payload);
        // check if container was found both according to its name and namespace
        final var dataNodeType = payload.getData().name().getNodeType();
        assertEquals("foo-bar-container", dataNodeType.getLocalName());
        assertEquals("bar:module", dataNodeType.getNamespace().toString());
    }
}
