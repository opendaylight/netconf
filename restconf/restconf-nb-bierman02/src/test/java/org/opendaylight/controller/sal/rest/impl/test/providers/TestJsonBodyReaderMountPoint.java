/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.rest.impl.test.providers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.util.Collection;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class TestJsonBodyReaderMountPoint extends AbstractBodyReaderTest {

    private final JsonNormalizedNodeBodyReader jsonBodyReader;
    private static SchemaContext schemaContext;

    private static final QNameModule INSTANCE_IDENTIFIER_MODULE_QNAME = initializeInstanceIdentifierModule();

    private static QNameModule initializeInstanceIdentifierModule() {
        try {
            return QNameModule.create(URI.create("instance:identifier:module"),
                SimpleDateFormatUtil.getRevisionFormat().parse("2014-01-17"));
        } catch (final ParseException e) {
            throw new Error(e);
        }
    }

    public TestJsonBodyReaderMountPoint() throws NoSuchFieldException,
            SecurityException {
        this.jsonBodyReader = new JsonNormalizedNodeBodyReader();
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

        final DOMMountPoint mountInstance = mock(DOMMountPoint.class);
        when(mountInstance.getSchemaContext()).thenReturn(schemaContext);
        final DOMMountPointService mockMountService = mock(DOMMountPointService.class);
        when(mockMountService.getMountPoint(any(YangInstanceIdentifier.class)))
                .thenReturn(Optional.of(mountInstance));

        ControllerContext.getInstance().setMountService(mockMountService);
        CONTROLLER_CONTEXT.setSchemas(schemaContext);
    }

    @Test
    public void moduleDataTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext
                .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final String uri = "instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont";
        mockBodyReader(uri, this.jsonBodyReader, false);
        final InputStream inputStream = TestJsonBodyReaderMountPoint.class
                .getResourceAsStream("/instanceidentifier/json/jsondata.json");
        final NormalizedNodeContext returnValue = this.jsonBodyReader.readFrom(null,
                null, null, this.mediaType, null, inputStream);
        checkMountPointNormalizedNodeContext(returnValue);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, returnValue);
    }

    @Test
    public void moduleSubContainerDataPutTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext
                .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final String uri = "instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont/cont1";
        mockBodyReader(uri, this.jsonBodyReader, false);
        final InputStream inputStream = TestJsonBodyReaderMountPoint.class
                .getResourceAsStream("/instanceidentifier/json/json_sub_container.json");
        final NormalizedNodeContext returnValue = this.jsonBodyReader.readFrom(null,
                null, null, this.mediaType, null, inputStream);
        checkMountPointNormalizedNodeContext(returnValue);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, returnValue,
                QName.create(dataSchemaNode.getQName(), "cont1"));
    }

    @Test
    public void moduleSubContainerDataPostTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext
                .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final String uri = "instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont";
        mockBodyReader(uri, this.jsonBodyReader, true);
        final InputStream inputStream = TestJsonBodyReaderMountPoint.class
                .getResourceAsStream("/instanceidentifier/json/json_sub_container.json");
        final NormalizedNodeContext returnValue = this.jsonBodyReader.readFrom(null,
                null, null, this.mediaType, null, inputStream);
        checkMountPointNormalizedNodeContext(returnValue);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, returnValue);
    }

    @Test
    public void rpcModuleInputTest() throws Exception {
        final String uri = "instance-identifier-module:cont/yang-ext:mount/invoke-rpc-module:rpc-test";
        mockBodyReader(uri, this.jsonBodyReader, true);
        final InputStream inputStream = TestJsonBodyReaderMountPoint.class
                .getResourceAsStream("/invoke-rpc/json/rpc-input.json");
        final NormalizedNodeContext returnValue = this.jsonBodyReader.readFrom(null,
                null, null, this.mediaType, null, inputStream);
        checkNormalizedNodeContext(returnValue);
        final ContainerNode inputNode = (ContainerNode) returnValue.getData();
        final YangInstanceIdentifier yangCont = YangInstanceIdentifier.of(QName
                .create(inputNode.getNodeType(), "cont"));
        final java.util.Optional<DataContainerChild<? extends PathArgument, ?>> contDataNode = inputNode
                .getChild(yangCont.getLastPathArgument());
        assertTrue(contDataNode.isPresent());
        assertTrue(contDataNode.get() instanceof ContainerNode);
        final YangInstanceIdentifier yangleaf = YangInstanceIdentifier.of(QName
                .create(inputNode.getNodeType(), "lf"));
        final java.util.Optional<DataContainerChild<? extends PathArgument, ?>> leafDataNode =
                ((ContainerNode) contDataNode.get()).getChild(yangleaf.getLastPathArgument());
        assertTrue(leafDataNode.isPresent());
        assertTrue("lf-test".equalsIgnoreCase(leafDataNode.get().getValue()
                .toString()));
    }

    private void checkExpectValueNormalizeNodeContext(
            final DataSchemaNode dataSchemaNode,
            final NormalizedNodeContext nnContext) {
        checkExpectValueNormalizeNodeContext(dataSchemaNode, nnContext, null);
    }

    protected void checkExpectValueNormalizeNodeContext(
            final DataSchemaNode dataSchemaNode,
            final NormalizedNodeContext nnContext, final QName qualifiedName) {
        YangInstanceIdentifier dataNodeIdent = YangInstanceIdentifier
                .of(dataSchemaNode.getQName());
        final DOMMountPoint mountPoint = nnContext
                .getInstanceIdentifierContext().getMountPoint();
        final DataSchemaNode mountDataSchemaNode = mountPoint
                .getSchemaContext().getDataChildByName(
                        dataSchemaNode.getQName());
        assertNotNull(mountDataSchemaNode);
        if (qualifiedName != null && dataSchemaNode instanceof DataNodeContainer) {
            final DataSchemaNode child = ((DataNodeContainer) dataSchemaNode)
                    .getDataChildByName(qualifiedName);
            dataNodeIdent = YangInstanceIdentifier.builder(dataNodeIdent)
                    .node(child.getQName()).build();
            assertTrue(nnContext.getInstanceIdentifierContext().getSchemaNode()
                    .equals(child));
        } else {
            assertTrue(mountDataSchemaNode.equals(dataSchemaNode));
        }
        assertNotNull(NormalizedNodes.findNode(nnContext.getData(),
                dataNodeIdent));
    }
}
