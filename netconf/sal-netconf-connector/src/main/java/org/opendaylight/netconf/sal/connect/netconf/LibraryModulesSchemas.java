/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static javax.xml.bind.DatatypeConverter.printBase64Binary;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.Module;
import org.opendaylight.yangtools.sal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XmlUtils;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.parser.DomToNormalizedNodeParserFactory;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * Holds URLs with YANG schema resources for all yang modules reported in
 * ietf-netconf-yang-library/modules-state/modules node
 */
public class LibraryModulesSchemas {

    private static final Logger LOG = LoggerFactory.getLogger(LibraryModulesSchemas.class);

    private static SchemaContext libraryContext;

    private final Map<SourceIdentifier, URL> availableModels;

    static {
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        moduleInfoBackedContext.registerModuleInfo(
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.
                        $YangModuleInfoImpl.getInstance());
        libraryContext = moduleInfoBackedContext.tryToCreateSchemaContext().get();
    }

    private LibraryModulesSchemas(final Map<SourceIdentifier, URL> availableModels) {
        this.availableModels = availableModels;
    }

    public Map<SourceIdentifier, URL> getAvailableModels() {
        return availableModels;
    }


    /**
     * Resolves URLs with YANG schema resources from modules-state. Uses basic http authenticaiton
     * @param url URL pointing to yang library
     * @return Resolved URLs with YANG schema resources for all yang modules from yang library
     */
    public static LibraryModulesSchemas create(final String url, final String username, final String password) {
        Preconditions.checkNotNull(url);
        try {
            final URL urlConnection = new URL(url);
            final URLConnection connection = urlConnection.openConnection();

            if(connection instanceof HttpURLConnection) {
                connection.setRequestProperty("Accept", "application/xml");
                String userpass = username + ":" + password;
                String basicAuth = "Basic " + printBase64Binary(userpass.getBytes());

                connection.setRequestProperty("Authorization", basicAuth);
            }

            return createFromURLConnection(connection);

        } catch (IOException e) {
            LOG.warn("Unable to download yang library from {}", url, e);
            return new LibraryModulesSchemas(Collections.<SourceIdentifier, URL>emptyMap());
        }
    }


    private static LibraryModulesSchemas createFromURLConnection(URLConnection connection) {

        String contentType = connection.getContentType();

        // TODO try to guess Json also from intput stream
        if (guessJsonFromFileName(connection.getURL().getFile())) {
            contentType = "application/json";
        }

        Preconditions.checkNotNull(contentType, "Content type unknown");
        Preconditions.checkState(contentType.equals("application/json") || contentType.equals("application/xml"),
                "Only XML and JSON types are supported.");
        try (final InputStream in = connection.getInputStream()) {
            final Optional<NormalizedNode<?, ?>> optionalModulesStateNode =
                    contentType.equals("application/json") ? readJson(in) : readXml(in);

            if (!optionalModulesStateNode.isPresent()) {
                return new LibraryModulesSchemas(Collections.<SourceIdentifier, URL>emptyMap());
            }

            final NormalizedNode<?, ?> modulesStateNode = optionalModulesStateNode.get();
            Preconditions.checkState(modulesStateNode.getNodeType().equals(ModulesState.QNAME),
                    "Wrong QName %s", modulesStateNode.getNodeType());
            Preconditions.checkState(modulesStateNode instanceof ContainerNode,
                    "Expecting container containing module list, but was %s", modulesStateNode);

            final YangInstanceIdentifier.NodeIdentifier moduleListNodeId =
                    new YangInstanceIdentifier.NodeIdentifier(Module.QNAME);
            final Optional<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> moduleListNode =
                    ((ContainerNode) modulesStateNode).getChild(moduleListNodeId);
            Preconditions.checkState(moduleListNode.isPresent(),
                    "Unable to find list: %s in %s", moduleListNodeId, modulesStateNode);
            Preconditions.checkState(moduleListNode.get() instanceof MapNode,
                    "Unexpected structure for container: %s in : %s. Expecting a list",
                    moduleListNodeId, modulesStateNode);

            final ImmutableMap.Builder<SourceIdentifier, URL> schemasMapping = new ImmutableMap.Builder<>();
            for (final MapEntryNode moduleNode : ((MapNode) moduleListNode.get()).getValue()) {
                final Optional<Map.Entry<SourceIdentifier, URL>> schemaMappingEntry = createFromEntry(moduleNode);
                if (schemaMappingEntry.isPresent()) {
                    schemasMapping.put(createFromEntry(moduleNode).get());
                }
            }

            return new LibraryModulesSchemas(schemasMapping.build());
        } catch (IOException e) {
            LOG.warn("Unable to download yang library from {}", connection.getURL(), e);
            return new LibraryModulesSchemas(Collections.<SourceIdentifier, URL>emptyMap());
        }
    }

    /**
     * Resolves URLs with YANG schema resources from modules-state
     * @param url URL pointing to yang library
     * @return Resolved URLs with YANG schema resources for all yang modules from yang library
     */
    public static LibraryModulesSchemas create(final String url) {
        Preconditions.checkNotNull(url);
        try {
            final URL urlConnection = new URL(url);
            final URLConnection connection = urlConnection.openConnection();

            if(connection instanceof HttpURLConnection) {
                connection.setRequestProperty("Accept", "application/xml");
            }

            return createFromURLConnection(connection);

        } catch (IOException e) {
            LOG.warn("Unable to download yang library from {}", url, e);
            return new LibraryModulesSchemas(Collections.<SourceIdentifier, URL>emptyMap());
        }
    }

    private static boolean guessJsonFromFileName(final String fileName) {
        String extension = "";
        final int i = fileName.lastIndexOf(46);
        if(i != -1) {
            extension = fileName.substring(i).toLowerCase();
        }

        return extension.equals(".json");
    }

    private static Optional<NormalizedNode<?, ?>> readJson(final InputStream in) {
        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        final JsonParserStream jsonParser = JsonParserStream.create(writer, libraryContext);
        final JsonReader reader = new JsonReader(new InputStreamReader(in));

        jsonParser.parse(reader);

        return resultHolder.isFinished() ?
                Optional.of(resultHolder.getResult()) :
                Optional.<NormalizedNode<?, ?>>absent();
    }

    private static Optional<NormalizedNode<?, ?>> readXml(final InputStream in) {
        final DomToNormalizedNodeParserFactory parserFactory =
                DomToNormalizedNodeParserFactory.getInstance(XmlUtils.DEFAULT_XML_CODEC_PROVIDER, libraryContext);

        try {
            final NormalizedNode<?, ?> parsed =
                    parserFactory.getContainerNodeParser().parse(Collections.singleton(XmlUtil.readXmlToElement(in)),
                            (ContainerSchemaNode) libraryContext.getDataChildByName(ModulesState.QNAME));
            return Optional.of(parsed);
        } catch (IOException|SAXException e) {
            LOG.warn("Unable to parse yang library xml content", e);
        }

        return Optional.<NormalizedNode<?, ?>>absent();
    }

    private static Optional<Map.Entry<SourceIdentifier, URL>> createFromEntry(final MapEntryNode moduleNode) {
        Preconditions.checkArgument(
                moduleNode.getNodeType().equals(Module.QNAME), "Wrong QName %s", moduleNode.getNodeType());

        YangInstanceIdentifier.NodeIdentifier childNodeId =
                new YangInstanceIdentifier.NodeIdentifier(QName.create(Module.QNAME, "name"));
        final String moduleName = getSingleChildNodeValue(moduleNode, childNodeId).get();

        childNodeId = new YangInstanceIdentifier.NodeIdentifier(QName.create(Module.QNAME, "revision"));
        final Optional<String> revision = getSingleChildNodeValue(moduleNode, childNodeId);
        if(revision.isPresent()) {
            if(!SourceIdentifier.REVISION_PATTERN.matcher(revision.get()).matches()) {
                LOG.warn("Skipping library schema for {}. Revision {} is in wrong format.", moduleNode, revision.get());
                return Optional.<Map.Entry<SourceIdentifier, URL>>absent();
            }
        }

        // FIXME leaf schema with url that represents the yang schema resource for this module is not mandatory
        // don't fail if schema node is not present, just skip the entry or add some default URL
        childNodeId = new YangInstanceIdentifier.NodeIdentifier(QName.create(Module.QNAME, "schema"));
        final Optional<String> schemaUriAsString = getSingleChildNodeValue(moduleNode, childNodeId);


        final SourceIdentifier sourceId = revision.isPresent()
                ? RevisionSourceIdentifier.create(moduleName, revision.get())
                : RevisionSourceIdentifier.create(moduleName);

        try {
            return Optional.<Map.Entry<SourceIdentifier, URL>>of(new AbstractMap.SimpleImmutableEntry<>(
                    sourceId, new URL(schemaUriAsString.get())));
        } catch (MalformedURLException e) {
            LOG.warn("Skipping library schema for {}. URL {} representing yang schema resource is not valid",
                    moduleNode, schemaUriAsString.get());
            return Optional.<Map.Entry<SourceIdentifier, URL>>absent();
        }
    }

    private static Optional<String> getSingleChildNodeValue(final DataContainerNode<?> schemaNode,
                                                            final YangInstanceIdentifier.NodeIdentifier childNodeId) {
        final Optional<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> node =
                schemaNode.getChild(childNodeId);
        Preconditions.checkArgument(node.isPresent(), "Child node %s not present", childNodeId.getNodeType());
        return getValueOfSimpleNode(node.get());
    }

    private static Optional<String> getValueOfSimpleNode(
            final NormalizedNode<? extends YangInstanceIdentifier.PathArgument, ?> node) {
        final Object value = node.getValue();
        return value == null || Strings.isNullOrEmpty(value.toString())
                ? Optional.<String>absent() : Optional.of(value.toString().trim());
    }

}
