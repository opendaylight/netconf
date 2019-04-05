/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static javax.xml.bind.DatatypeConverter.printBase64Binary;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_DATA_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_PATH;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toId;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.Module;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Holds URLs with YANG schema resources for all yang modules reported in
 * ietf-netconf-yang-library/modules-state/modules node.
 */
public final class LibraryModulesSchemas implements NetconfDeviceSchemas {

    private static final Logger LOG = LoggerFactory.getLogger(LibraryModulesSchemas.class);
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final SchemaContext LIBRARY_CONTEXT;

    static {
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        moduleInfoBackedContext.registerModuleInfo(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang
                .library.rev160621.$YangModuleInfoImpl.getInstance());
        LIBRARY_CONTEXT = moduleInfoBackedContext.tryToCreateSchemaContext().get();
    }

    private static final JSONCodecFactory JSON_CODECS = JSONCodecFactorySupplier.DRAFT_LHOTKA_NETMOD_YANG_JSON_02
            .getShared(LIBRARY_CONTEXT);

    private final Map<QName, URL> availableModels;

    private static final YangInstanceIdentifier MODULES_STATE_MODULE_LIST =
            YangInstanceIdentifier.builder().node(ModulesState.QNAME).node(Module.QNAME).build();

    private static final ContainerNode GET_MODULES_STATE_MODULE_LIST_RPC = Builders.containerBuilder()
            .withNodeIdentifier(NETCONF_GET_NODEID)
            .withChild(NetconfMessageTransformUtil.toFilterStructure(MODULES_STATE_MODULE_LIST, LIBRARY_CONTEXT))
            .build();

    private LibraryModulesSchemas(final Map<QName, URL> availableModels) {
        this.availableModels = availableModels;
    }

    public Map<SourceIdentifier, URL> getAvailableModels() {
        final Map<SourceIdentifier, URL> result = new HashMap<>();
        for (final Map.Entry<QName, URL> entry : availableModels.entrySet()) {
            final SourceIdentifier sId = RevisionSourceIdentifier.create(entry.getKey().getLocalName(),
                entry.getKey().getRevision());
            result.put(sId, entry.getValue());
        }

        return result;
    }

    /**
     * Resolves URLs with YANG schema resources from modules-state. Uses basic http authenticaiton
     *
     * @param url URL pointing to yang library
     * @return Resolved URLs with YANG schema resources for all yang modules from yang library
     */
    public static LibraryModulesSchemas create(final String url, final String username, final String password) {
        Preconditions.checkNotNull(url);
        try {
            final URL urlConnection = new URL(url);
            final URLConnection connection = urlConnection.openConnection();

            if (connection instanceof HttpURLConnection) {
                connection.setRequestProperty("Accept", "application/xml");
                final String userpass = username + ":" + password;
                final String basicAuth = "Basic " + printBase64Binary(userpass.getBytes(StandardCharsets.UTF_8));

                connection.setRequestProperty("Authorization", basicAuth);
            }

            return createFromURLConnection(connection);

        } catch (final IOException e) {
            LOG.warn("Unable to download yang library from {}", url, e);
            return new LibraryModulesSchemas(Collections.emptyMap());
        }
    }


    public static LibraryModulesSchemas create(final NetconfDeviceRpc deviceRpc, final RemoteDeviceId deviceId) {
        final DOMRpcResult moduleListNodeResult;
        try {
            moduleListNodeResult =
                    deviceRpc.invokeRpc(NETCONF_GET_PATH, GET_MODULES_STATE_MODULE_LIST_RPC).get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(deviceId + ": Interrupted while waiting for response to "
                    + MODULES_STATE_MODULE_LIST, e);
        } catch (final ExecutionException e) {
            LOG.warn("{}: Unable to detect available schemas, get to {} failed", deviceId,
                    MODULES_STATE_MODULE_LIST, e);
            return new LibraryModulesSchemas(Collections.emptyMap());
        }

        if (moduleListNodeResult.getErrors().isEmpty() == false) {
            LOG.warn("{}: Unable to detect available schemas, get to {} failed, {}",
                    deviceId, MODULES_STATE_MODULE_LIST, moduleListNodeResult.getErrors());
            return new LibraryModulesSchemas(Collections.emptyMap());
        }


        final Optional<? extends NormalizedNode<?, ?>> modulesStateNode =
                findModulesStateNode(moduleListNodeResult.getResult());
        if (modulesStateNode.isPresent()) {
            Preconditions.checkState(modulesStateNode.get() instanceof ContainerNode,
                    "Expecting container containing schemas, but was %s", modulesStateNode.get());
            return create((ContainerNode) modulesStateNode.get());
        }

        LOG.warn("{}: Unable to detect available schemas, get to {} was empty", deviceId, toId(ModulesState.QNAME));
        return new LibraryModulesSchemas(Collections.emptyMap());
    }

    private static LibraryModulesSchemas create(final ContainerNode modulesStateNode) {
        final YangInstanceIdentifier.NodeIdentifier moduleListNodeId =
                new YangInstanceIdentifier.NodeIdentifier(Module.QNAME);
        final Optional<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> moduleListNode =
                modulesStateNode.getChild(moduleListNodeId);
        Preconditions.checkState(moduleListNode.isPresent(),
                "Unable to find list: %s in %s", moduleListNodeId, modulesStateNode);
        Preconditions.checkState(moduleListNode.get() instanceof MapNode,
                "Unexpected structure for container: %s in : %s. Expecting a list",
                moduleListNodeId, modulesStateNode);

        final ImmutableMap.Builder<QName, URL> schemasMapping = new ImmutableMap.Builder<>();
        for (final MapEntryNode moduleNode : ((MapNode) moduleListNode.get()).getValue()) {
            final Optional<Map.Entry<QName, URL>> schemaMappingEntry = createFromEntry(moduleNode);
            if (schemaMappingEntry.isPresent()) {
                schemasMapping.put(createFromEntry(moduleNode).get());
            }
        }

        return new LibraryModulesSchemas(schemasMapping.build());
    }

    /**
     * Resolves URLs with YANG schema resources from modules-state.
     * @param url URL pointing to yang library
     * @return Resolved URLs with YANG schema resources for all yang modules from yang library
     */
    public static LibraryModulesSchemas create(final String url) {
        Preconditions.checkNotNull(url);
        try {
            final URL urlConnection = new URL(url);
            final URLConnection connection = urlConnection.openConnection();

            if (connection instanceof HttpURLConnection) {
                connection.setRequestProperty("Accept", "application/xml");
            }

            return createFromURLConnection(connection);

        } catch (final IOException e) {
            LOG.warn("Unable to download yang library from {}", url, e);
            return new LibraryModulesSchemas(Collections.emptyMap());
        }
    }

    private static Optional<? extends NormalizedNode<?, ?>> findModulesStateNode(final NormalizedNode<?, ?> result) {
        if (result == null) {
            return Optional.empty();
        }
        final Optional<DataContainerChild<?, ?>> dataNode =
                ((DataContainerNode<?>) result).getChild(NETCONF_DATA_NODEID);
        if (dataNode.isPresent() == false) {
            return Optional.empty();
        }

        return ((DataContainerNode<?>) dataNode.get()).getChild(toId(ModulesState.QNAME));
    }

    private static LibraryModulesSchemas createFromURLConnection(final URLConnection connection) {

        String contentType = connection.getContentType();

        // TODO try to guess Json also from intput stream
        if (guessJsonFromFileName(connection.getURL().getFile())) {
            contentType = "application/json";
        }

        Preconditions.checkNotNull(contentType, "Content type unknown");
        Preconditions.checkState(contentType.equals("application/json") || contentType.equals("application/xml"),
                "Only XML and JSON types are supported.");
        try (InputStream in = connection.getInputStream()) {
            final Optional<NormalizedNode<?, ?>> optionalModulesStateNode =
                    contentType.equals("application/json") ? readJson(in) : readXml(in);

            if (!optionalModulesStateNode.isPresent()) {
                return new LibraryModulesSchemas(Collections.emptyMap());
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

            final ImmutableMap.Builder<QName, URL> schemasMapping = new ImmutableMap.Builder<>();
            for (final MapEntryNode moduleNode : ((MapNode) moduleListNode.get()).getValue()) {
                final Optional<Map.Entry<QName, URL>> schemaMappingEntry = createFromEntry(moduleNode);
                if (schemaMappingEntry.isPresent()) {
                    schemasMapping.put(createFromEntry(moduleNode).get());
                }
            }

            return new LibraryModulesSchemas(schemasMapping.build());
        } catch (final IOException e) {
            LOG.warn("Unable to download yang library from {}", connection.getURL(), e);
            return new LibraryModulesSchemas(Collections.emptyMap());
        }
    }

    private static boolean guessJsonFromFileName(final String fileName) {
        String extension = "";
        final int i = fileName.lastIndexOf(46);
        if (i != -1) {
            extension = fileName.substring(i).toLowerCase(Locale.ROOT);
        }

        return extension.equals(".json");
    }

    private static Optional<NormalizedNode<?, ?>> readJson(final InputStream in) {
        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        final JsonParserStream jsonParser = JsonParserStream.create(writer, JSON_CODECS);
        final JsonReader reader = new JsonReader(new InputStreamReader(in, Charset.defaultCharset()));

        jsonParser.parse(reader);

        return resultHolder.isFinished() ? Optional.of(resultHolder.getResult()) : Optional.empty();
    }

    private static Optional<NormalizedNode<?, ?>> readXml(final InputStream in) {
        try {
            final DocumentBuilder docBuilder = UntrustedXML.newDocumentBuilder();

            final Document read = docBuilder.parse(in);
            final Document doc = docBuilder.newDocument();
            final Element rootElement = doc.createElementNS("urn:ietf:params:xml:ns:yang:ietf-yang-library",
                    "modules");
            doc.appendChild(rootElement);

            for (int i = 0; i < read.getElementsByTagName("revision").getLength(); i++) {
                final String revision = read.getElementsByTagName("revision").item(i).getTextContent();
                if (DATE_PATTERN.matcher(revision).find() || revision.isEmpty()) {
                    final Node module = doc.importNode(read.getElementsByTagName("module").item(i), true);
                    rootElement.appendChild(module);
                } else {
                    LOG.warn("Xml contains wrong revision - {} - on module {}", revision,
                            read.getElementsByTagName("module").item(i).getTextContent());
                }
            }

            final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
            final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
            final XmlParserStream xmlParser = XmlParserStream.create(writer, LIBRARY_CONTEXT,
                    LIBRARY_CONTEXT.getDataChildByName(ModulesState.QNAME));
            xmlParser.traverse(new DOMSource(doc.getDocumentElement()));
            final NormalizedNode<?, ?> parsed = resultHolder.getResult();
            return Optional.of(parsed);
        } catch (XMLStreamException | URISyntaxException | IOException | SAXException e) {
            LOG.warn("Unable to parse yang library xml content", e);
        }

        return Optional.empty();
    }

    private static Optional<Map.Entry<QName, URL>> createFromEntry(final MapEntryNode moduleNode) {
        Preconditions.checkArgument(
                moduleNode.getNodeType().equals(Module.QNAME), "Wrong QName %s", moduleNode.getNodeType());

        YangInstanceIdentifier.NodeIdentifier childNodeId =
                new YangInstanceIdentifier.NodeIdentifier(QName.create(Module.QNAME, "name"));
        final String moduleName = getSingleChildNodeValue(moduleNode, childNodeId).get();

        childNodeId = new YangInstanceIdentifier.NodeIdentifier(QName.create(Module.QNAME, "revision"));
        final Optional<String> revision = getSingleChildNodeValue(moduleNode, childNodeId);
        if (revision.isPresent()) {
            if (!Revision.STRING_FORMAT_PATTERN.matcher(revision.get()).matches()) {
                LOG.warn("Skipping library schema for {}. Revision {} is in wrong format.", moduleNode, revision.get());
                return Optional.empty();
            }
        }

        // FIXME leaf schema with url that represents the yang schema resource for this module is not mandatory
        // don't fail if schema node is not present, just skip the entry or add some default URL
        childNodeId = new YangInstanceIdentifier.NodeIdentifier(QName.create(Module.QNAME, "schema"));
        final Optional<String> schemaUriAsString = getSingleChildNodeValue(moduleNode, childNodeId);

        childNodeId = new YangInstanceIdentifier.NodeIdentifier(QName.create(Module.QNAME, "namespace"));
        final String moduleNameSpace = getSingleChildNodeValue(moduleNode, childNodeId).get();

        final QName moduleQName = revision.isPresent()
                ? QName.create(moduleNameSpace, revision.get(), moduleName)
                : QName.create(URI.create(moduleNameSpace), moduleName);

        try {
            return Optional.of(new AbstractMap.SimpleImmutableEntry<>(
                    moduleQName, new URL(schemaUriAsString.get())));
        } catch (final MalformedURLException e) {
            LOG.warn("Skipping library schema for {}. URL {} representing yang schema resource is not valid",
                    moduleNode, schemaUriAsString.get());
            return Optional.empty();
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
        final String valueStr = node.getValue().toString();
        return Strings.isNullOrEmpty(valueStr) ? Optional.empty() : Optional.of(valueStr.trim());
    }

    @Override
    public Set<QName> getAvailableYangSchemasQNames() {
        return null;
    }
}
