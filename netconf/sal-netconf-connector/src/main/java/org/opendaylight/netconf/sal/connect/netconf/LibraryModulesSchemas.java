/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_DATA_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_QNAME;

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
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.shaded.xerces.impl.dv.util.Base64;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
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
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
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
    private static final EffectiveModelContext LIBRARY_CONTEXT = BindingRuntimeHelpers.createEffectiveModel(
        YangLibrary.class);

    // FIXME: this is legacy RFC7895, add support for RFC8525 containers, too
    private static final NodeIdentifier MODULES_STATE_NID = NodeIdentifier.create(ModulesState.QNAME);
    private static final NodeIdentifier MODULE_NID = NodeIdentifier.create(Module.QNAME);
    private static final NodeIdentifier NAME_NID = NodeIdentifier.create(QName.create(Module.QNAME, "name").intern());
    private static final NodeIdentifier REVISION_NID = NodeIdentifier.create(
        QName.create(Module.QNAME, "revision").intern());
    private static final NodeIdentifier SCHEMA_NID = NodeIdentifier.create(
        QName.create(Module.QNAME, "schema").intern());
    private static final NodeIdentifier NAMESPACE_NID = NodeIdentifier.create(
        QName.create(Module.QNAME, "namespace").intern());

    private static final JSONCodecFactory JSON_CODECS = JSONCodecFactorySupplier.DRAFT_LHOTKA_NETMOD_YANG_JSON_02
            .getShared(LIBRARY_CONTEXT);

    private static final YangInstanceIdentifier MODULES_STATE_MODULE_LIST =
            YangInstanceIdentifier.create(MODULES_STATE_NID, MODULE_NID);

    private static final ContainerNode GET_MODULES_STATE_MODULE_LIST_RPC = Builders.containerBuilder()
            .withNodeIdentifier(NETCONF_GET_NODEID)
            .withChild(NetconfMessageTransformUtil.toFilterStructure(MODULES_STATE_MODULE_LIST, LIBRARY_CONTEXT))
            .build();

    private final ImmutableMap<QName, URL> availableModels;

    private LibraryModulesSchemas(final ImmutableMap<QName, URL> availableModels) {
        this.availableModels = requireNonNull(availableModels);
    }

    public Map<SourceIdentifier, URL> getAvailableModels() {
        final Map<SourceIdentifier, URL> result = new HashMap<>();
        for (final Entry<QName, URL> entry : availableModels.entrySet()) {
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
        try {
            final URL urlConnection = new URL(requireNonNull(url));
            final URLConnection connection = urlConnection.openConnection();

            if (connection instanceof HttpURLConnection) {
                connection.setRequestProperty("Accept", "application/xml");
                final String userpass = username + ":" + password;
                final String basicAuth = "Basic " + Base64.encode(userpass.getBytes(StandardCharsets.UTF_8));

                connection.setRequestProperty("Authorization", basicAuth);
            }

            return createFromURLConnection(connection);

        } catch (final IOException e) {
            LOG.warn("Unable to download yang library from {}", url, e);
            return new LibraryModulesSchemas(ImmutableMap.of());
        }
    }


    public static LibraryModulesSchemas create(final NetconfDeviceRpc deviceRpc, final RemoteDeviceId deviceId) {
        final DOMRpcResult moduleListNodeResult;
        try {
            moduleListNodeResult =
                    deviceRpc.invokeRpc(NETCONF_GET_QNAME, GET_MODULES_STATE_MODULE_LIST_RPC).get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(deviceId + ": Interrupted while waiting for response to "
                    + MODULES_STATE_MODULE_LIST, e);
        } catch (final ExecutionException e) {
            LOG.warn("{}: Unable to detect available schemas, get to {} failed", deviceId,
                    MODULES_STATE_MODULE_LIST, e);
            return new LibraryModulesSchemas(ImmutableMap.of());
        }

        if (moduleListNodeResult.getErrors().isEmpty() == false) {
            LOG.warn("{}: Unable to detect available schemas, get to {} failed, {}",
                    deviceId, MODULES_STATE_MODULE_LIST, moduleListNodeResult.getErrors());
            return new LibraryModulesSchemas(ImmutableMap.of());
        }

        final Optional<? extends NormalizedNode<?, ?>> modulesStateNode =
                findModulesStateNode(moduleListNodeResult.getResult());
        if (modulesStateNode.isPresent()) {
            final NormalizedNode<?, ?> node = modulesStateNode.get();
            checkState(node instanceof ContainerNode, "Expecting container containing schemas, but was %s", node);
            return create((ContainerNode) node);
        }

        LOG.warn("{}: Unable to detect available schemas, get to {} was empty", deviceId, MODULES_STATE_NID);
        return new LibraryModulesSchemas(ImmutableMap.of());
    }

    private static LibraryModulesSchemas create(final ContainerNode modulesStateNode) {
        final Optional<DataContainerChild<?, ?>> moduleListNode = modulesStateNode.getChild(MODULE_NID);
        checkState(moduleListNode.isPresent(), "Unable to find list: %s in %s", MODULE_NID, modulesStateNode);
        final DataContainerChild<?, ?> node = moduleListNode.get();
        checkState(node instanceof MapNode, "Unexpected structure for container: %s in : %s. Expecting a list",
            MODULE_NID, modulesStateNode);

        final MapNode moduleList = (MapNode) node;
        final Collection<MapEntryNode> modules = moduleList.getValue();
        final ImmutableMap.Builder<QName, URL> schemasMapping = ImmutableMap.builderWithExpectedSize(modules.size());
        for (final MapEntryNode moduleNode : modules) {
            final Entry<QName, URL> entry = createFromEntry(moduleNode);
            if (entry != null) {
                schemasMapping.put(entry);
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
        final URLConnection connection;
        try {
            connection = new URL(requireNonNull(url)).openConnection();
        } catch (final IOException e) {
            LOG.warn("Unable to download yang library from {}", url, e);
            return new LibraryModulesSchemas(ImmutableMap.of());
        }

        if (connection instanceof HttpURLConnection) {
            connection.setRequestProperty("Accept", "application/xml");
        }
        return createFromURLConnection(connection);
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

        return ((DataContainerNode<?>) dataNode.get()).getChild(MODULES_STATE_NID);
    }

    private static LibraryModulesSchemas createFromURLConnection(final URLConnection connection) {

        String contentType = connection.getContentType();

        // TODO try to guess Json also from intput stream
        if (guessJsonFromFileName(connection.getURL().getFile())) {
            contentType = "application/json";
        }

        requireNonNull(contentType, "Content type unknown");
        checkState(contentType.equals("application/json") || contentType.equals("application/xml"),
                "Only XML and JSON types are supported.");

        Optional<NormalizedNode<?, ?>> optionalModulesStateNode = Optional.empty();
        try (InputStream in = connection.getInputStream()) {
            optionalModulesStateNode = contentType.equals("application/json") ? readJson(in) : readXml(in);
        } catch (final IOException e) {
            LOG.warn("Unable to download yang library from {}", connection.getURL(), e);
        }

        if (!optionalModulesStateNode.isPresent()) {
            return new LibraryModulesSchemas(ImmutableMap.of());
        }

        final NormalizedNode<?, ?> modulesStateNode = optionalModulesStateNode.get();
        checkState(modulesStateNode instanceof ContainerNode, "Expecting container containing module list, but was %s",
            modulesStateNode);
        final ContainerNode modulesState = (ContainerNode) modulesStateNode;
        final NodeIdentifier nodeName = modulesState.getIdentifier();
        checkState(MODULES_STATE_NID.equals(nodeName), "Wrong container identifier %s", nodeName);

        return create((ContainerNode) modulesStateNode);
    }

    private static boolean guessJsonFromFileName(final String fileName) {
        final int i = fileName.lastIndexOf('.');
        return i != 1 && ".json".equalsIgnoreCase(fileName.substring(i));
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
                    LIBRARY_CONTEXT.findDataChildByName(ModulesState.QNAME).orElseThrow());
            xmlParser.traverse(new DOMSource(doc.getDocumentElement()));
            final NormalizedNode<?, ?> parsed = resultHolder.getResult();
            return Optional.of(parsed);
        } catch (XMLStreamException | URISyntaxException | IOException | SAXException e) {
            LOG.warn("Unable to parse yang library xml content", e);
        }

        return Optional.empty();
    }

    private static @Nullable Entry<QName, URL> createFromEntry(final MapEntryNode moduleNode) {
        checkArgument(moduleNode.getNodeType().equals(Module.QNAME), "Wrong QName %s", moduleNode.getNodeType());

        final String moduleName = getSingleChildNodeValue(moduleNode, NAME_NID).get();
        final Optional<String> revision = getSingleChildNodeValue(moduleNode, REVISION_NID);
        if (revision.isPresent()) {
            if (!Revision.STRING_FORMAT_PATTERN.matcher(revision.get()).matches()) {
                LOG.warn("Skipping library schema for {}. Revision {} is in wrong format.", moduleNode, revision.get());
                return null;
            }
        }

        // FIXME leaf schema with url that represents the yang schema resource for this module is not mandatory
        // don't fail if schema node is not present, just skip the entry or add some default URL
        final Optional<String> schemaUriAsString = getSingleChildNodeValue(moduleNode, SCHEMA_NID);
        final String moduleNameSpace = getSingleChildNodeValue(moduleNode, NAMESPACE_NID).get();

        final QName moduleQName = revision.isPresent()
                ? QName.create(moduleNameSpace, revision.get(), moduleName)
                : QName.create(URI.create(moduleNameSpace), moduleName);

        try {
            return new SimpleImmutableEntry<>(moduleQName, new URL(schemaUriAsString.get()));
        } catch (final MalformedURLException e) {
            LOG.warn("Skipping library schema for {}. URL {} representing yang schema resource is not valid",
                    moduleNode, schemaUriAsString.get());
            return null;
        }
    }

    private static Optional<String> getSingleChildNodeValue(final DataContainerNode<?> schemaNode,
                                                            final NodeIdentifier childNodeId) {
        final Optional<DataContainerChild<?, ?>> node = schemaNode.getChild(childNodeId);
        checkArgument(node.isPresent(), "Child node %s not present", childNodeId.getNodeType());
        return getValueOfSimpleNode(node.get());
    }

    private static Optional<String> getValueOfSimpleNode(final NormalizedNode<?, ?> node) {
        final String valueStr = node.getValue().toString();
        return Strings.isNullOrEmpty(valueStr) ? Optional.empty() : Optional.of(valueStr.trim());
    }

    @Override
    public Set<QName> getAvailableYangSchemasQNames() {
        return null;
    }
}
