/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.md.sal.rest.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableClassToInstanceMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.controller.sal.rest.impl.test.providers.TestJsonBodyWriter;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public final class TestRestconfUtils {

    private static final Logger LOG = LoggerFactory.getLogger(TestRestconfUtils.class);

    private TestRestconfUtils() {
        throw new UnsupportedOperationException("Test utility class");
    }

    public static ControllerContext newControllerContext(final EffectiveModelContext schemaContext) {
        return newControllerContext(schemaContext, null);
    }

    public static ControllerContext newControllerContext(final EffectiveModelContext schemaContext,
            final DOMMountPoint mountInstance) {
        final DOMMountPointService mockMountService = mock(DOMMountPointService.class);

        if (mountInstance != null) {
            doReturn(Optional.of(FixedDOMSchemaService.of(() -> schemaContext))).when(mountInstance)
                .getService(eq(DOMSchemaService.class));
            doReturn(Optional.ofNullable(mountInstance)).when(mockMountService).getMountPoint(
                any(YangInstanceIdentifier.class));
        }

        DOMSchemaService mockSchemaService = mock(DOMSchemaService.class);
        doReturn(schemaContext).when(mockSchemaService).getGlobalContext();

        DOMSchemaService mockDomSchemaService = mock(DOMSchemaService.class);
        doReturn(ImmutableClassToInstanceMap.of()).when(mockDomSchemaService).getExtensions();

        return ControllerContext.newInstance(mockSchemaService, mockMountService, mockDomSchemaService);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static EffectiveModelContext loadSchemaContext(final String yangPath,
            final EffectiveModelContext schemaContext) {
        try {
            Preconditions.checkArgument(yangPath != null, "Path can not be null.");
            Preconditions.checkArgument(!yangPath.isEmpty(), "Path can not be empty.");
            if (schemaContext == null) {
                return YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(yangPath));
            } else {
                throw new UnsupportedOperationException("Unable to add new yang sources to existing schema context.");
            }
        } catch (final Exception e) {
            LOG.error("Yang files at path: " + yangPath + " weren't loaded.", e);
        }
        return schemaContext;
    }

    public static NormalizedNodeContext loadNormalizedContextFromJsonFile() {
        throw new AbstractMethodError("Not implemented yet");
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static NormalizedNodeContext loadNormalizedContextFromXmlFile(final String pathToInputFile,
            final String uri, final ControllerContext controllerContext) {
        final InstanceIdentifierContext iiContext = controllerContext.toInstanceIdentifier(uri);
        final InputStream inputStream = TestJsonBodyWriter.class.getResourceAsStream(pathToInputFile);
        try {
            final Document doc = UntrustedXML.newDocumentBuilder().parse(inputStream);
            final NormalizedNode nn = parse(iiContext, doc);
            return new NormalizedNodeContext(iiContext, nn);
        } catch (final Exception e) {
            LOG.error("Load xml file " + pathToInputFile + " fail.", e);
        }
        return null;
    }

    private static NormalizedNode parse(final InstanceIdentifierContext iiContext, final Document doc)
            throws XMLStreamException, IOException, SAXException, URISyntaxException {
        final SchemaNode schemaNodeContext = iiContext.getSchemaNode();
        final SchemaInferenceStack stack;
        DataSchemaNode schemaNode = null;
        if (schemaNodeContext instanceof RpcDefinition) {
            final var rpc = (RpcDefinition) schemaNodeContext;
            stack = SchemaInferenceStack.of(iiContext.getSchemaContext());
            stack.enterSchemaTree(rpc.getQName());
            if ("input".equalsIgnoreCase(doc.getDocumentElement().getLocalName())) {
                schemaNode = rpc.getInput();
            } else if ("output".equalsIgnoreCase(doc.getDocumentElement().getLocalName())) {
                schemaNode = rpc.getOutput();
            } else {
                throw new IllegalStateException("Unknown Rpc input node");
            }
            stack.enterSchemaTree(schemaNode.getQName());
        } else if (schemaNodeContext instanceof DataSchemaNode) {
            schemaNode = (DataSchemaNode) schemaNodeContext;
            stack = iiContext.inference().toSchemaInferenceStack();
        } else {
            throw new IllegalStateException("Unknow SchemaNode");
        }

        final String docRootElm = doc.getDocumentElement().getLocalName();
        final String schemaNodeName = iiContext.getSchemaNode().getQName().getLocalName();

        if (!schemaNodeName.equalsIgnoreCase(docRootElm)) {
            for (final DataSchemaNode child : ((DataNodeContainer) schemaNode).getChildNodes()) {
                if (child.getQName().getLocalName().equalsIgnoreCase(docRootElm)) {
                    schemaNode = child;
                    stack.enterSchemaTree(child.getQName());
                    break;
                }
            }
        }

        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        final XmlParserStream xmlParser = XmlParserStream.create(writer, stack.toInference());

        if (schemaNode instanceof ContainerLike || schemaNode instanceof ListSchemaNode) {
            xmlParser.traverse(new DOMSource(doc.getDocumentElement()));
            return resultHolder.getResult();
        }
        // FIXME : add another DataSchemaNode extensions e.g. LeafSchemaNode
        return null;
    }

    public static Collection<File> loadFiles(final String resourceDirectory) throws FileNotFoundException {
        final String path = TestRestconfUtils.class.getResource(resourceDirectory).getPath();
        final File testDir = new File(path);
        final String[] fileList = testDir.list();
        final List<File> testFiles = new ArrayList<>();
        if (fileList == null) {
            throw new FileNotFoundException(resourceDirectory);
        }
        for (final String fileName : fileList) {
            if (new File(testDir, fileName).isDirectory() == false) {
                testFiles.add(new File(testDir, fileName));
            }
        }
        return testFiles;
    }
}
