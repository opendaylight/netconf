/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
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

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static NormalizedNodePayload loadNormalizedContextFromXmlFile(final String pathToInputFile,
            final String uri, final EffectiveModelContext schemaContext) {
        final InstanceIdentifierContext iiContext = ParserIdentifier.toInstanceIdentifier(uri, schemaContext, null);
        final InputStream inputStream = TestRestconfUtils.class.getResourceAsStream(pathToInputFile);
        try {
            final Document doc = UntrustedXML.newDocumentBuilder().parse(inputStream);
            final NormalizedNode nn = parse(iiContext, doc);
            return NormalizedNodePayload.of(iiContext, nn);
        } catch (final Exception e) {
            LOG.error("Load xml file {} fail.", pathToInputFile, e);
        }
        return null;
    }

    private static NormalizedNode parse(final InstanceIdentifierContext iiContext, final Document doc)
            throws XMLStreamException, IOException, ParserConfigurationException, SAXException, URISyntaxException {
        final SchemaNode schemaNodeContext = iiContext.getSchemaNode();
        DataSchemaNode schemaNode = null;
        if (schemaNodeContext instanceof RpcDefinition) {
            if ("input".equalsIgnoreCase(doc.getDocumentElement().getLocalName())) {
                schemaNode = ((RpcDefinition) schemaNodeContext).getInput();
            } else if ("output".equalsIgnoreCase(doc.getDocumentElement().getLocalName())) {
                schemaNode = ((RpcDefinition) schemaNodeContext).getOutput();
            } else {
                throw new IllegalStateException("Unknown Rpc input node");
            }

        } else if (schemaNodeContext instanceof DataSchemaNode) {
            schemaNode = (DataSchemaNode) schemaNodeContext;
        } else {
            throw new IllegalStateException("Unknow SchemaNode");
        }

        final String docRootElm = doc.getDocumentElement().getLocalName();
        final String schemaNodeName = iiContext.getSchemaNode().getQName().getLocalName();

        if (!schemaNodeName.equalsIgnoreCase(docRootElm)) {
            for (final DataSchemaNode child : ((DataNodeContainer) schemaNode).getChildNodes()) {
                if (child.getQName().getLocalName().equalsIgnoreCase(docRootElm)) {
                    schemaNode = child;
                    break;
                }
            }
        }

        final NormalizationResultHolder resultHolder = new NormalizationResultHolder();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        final XmlParserStream xmlParser = XmlParserStream.create(writer, iiContext.inference());

        if (schemaNode instanceof ContainerSchemaNode || schemaNode instanceof ListSchemaNode) {
            xmlParser.traverse(new DOMSource(doc.getDocumentElement()));
            return resultHolder.getResult().data();
        }
        // FIXME : add another DataSchemaNode extensions e.g. LeafSchemaNode
        return null;
    }

    public static List<File> loadFiles(final String resourceDirectory) throws FileNotFoundException {
        final String path = TestRestconfUtils.class.getResource(resourceDirectory).getPath();
        final File testDir = new File(path);
        final String[] fileList = testDir.list();
        final List<File> testFiles = new ArrayList<>();
        if (fileList == null) {
            throw new FileNotFoundException(resourceDirectory);
        }
        for (final String fileName : fileList) {
            if (fileName.endsWith(YangConstants.RFC6020_YANG_FILE_EXTENSION)
                && !new File(testDir, fileName).isDirectory()) {
                testFiles.add(new File(testDir, fileName));
            }
        }
        return testFiles;
    }
}
