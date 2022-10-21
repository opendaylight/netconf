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
import static com.google.common.base.Verify.verify;
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
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
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
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
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
public final class LibraryModulesSchemasFactory {
    private static final Logger LOG = LoggerFactory.getLogger(LibraryModulesSchemasFactory.class);
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

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
    private static final YangInstanceIdentifier MODULES_STATE_MODULE_LIST =
        YangInstanceIdentifier.create(MODULES_STATE_NID, MODULE_NID);

    private final @NonNull EffectiveModelContext libraryContext;
    private final @NonNull JSONCodecFactory jsonCodecs;

    public LibraryModulesSchemasFactory(final YangParserFactory parserFactory) throws YangParserException {
        libraryContext = BindingRuntimeHelpers.createEffectiveModel(parserFactory,
            List.of($YangModuleInfoImpl.getInstance()));
        // FIXME: migrate to RFC7951
        jsonCodecs = JSONCodecFactorySupplier.DRAFT_LHOTKA_NETMOD_YANG_JSON_02.getShared(libraryContext);
    }

    /**
     * Resolves URLs with YANG schema resources from modules-state. Uses basic http authenticaiton
     *
     * @param url URL pointing to yang library
     * @return Resolved URLs with YANG schema resources for all yang modules from yang library
     */
    public LibraryModulesSchemas create(final String url, final String username, final String password) {
        try {
            final URL urlConnection = new URL(requireNonNull(url));
            final URLConnection connection = urlConnection.openConnection();

            if (connection instanceof HttpURLConnection) {
                connection.setRequestProperty("Accept", "application/xml");
                final String userpass = username + ":" + password;
                connection.setRequestProperty("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(userpass.getBytes(StandardCharsets.UTF_8)));
            }

            return createFromURLConnection(connection);

        } catch (final IOException e) {
            LOG.warn("Unable to download yang library from {}", url, e);
            return new LibraryModulesSchemas(ImmutableMap.of());
        }
    }

    public LibraryModulesSchemas create(final NetconfDeviceRpc deviceRpc, final RemoteDeviceId deviceId,
            final EffectiveModelContext context) {
        final DOMRpcResult moduleListNodeResult;
        try {
            moduleListNodeResult = deviceRpc.invokeRpc(NETCONF_GET_QNAME, Builders.containerBuilder()
                .withNodeIdentifier(NETCONF_GET_NODEID)
                .withChild(NetconfMessageTransformUtil.toFilterStructure(MODULES_STATE_MODULE_LIST, libraryContext))
                .build())
                .get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(deviceId + ": Interrupted while waiting for response to "
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

        final Optional<DataContainerChild> modulesStateNode =
                findModulesStateNode(moduleListNodeResult.getResult(), context);
        if (modulesStateNode.isPresent()) {
            final DataContainerChild node = modulesStateNode.get();
            checkState(node instanceof ContainerNode, "Expecting container containing schemas, but was %s", node);
            return create((ContainerNode) node);
        }

        LOG.warn("{}: Unable to detect available schemas, get to {} was empty", deviceId, MODULES_STATE_NID);
        return new LibraryModulesSchemas(ImmutableMap.of());
    }

    private static LibraryModulesSchemas create(final ContainerNode modulesStateNode) {
        final Optional<DataContainerChild> moduleListNode = modulesStateNode.findChildByArg(MODULE_NID);
        checkState(moduleListNode.isPresent(), "Unable to find list: %s in %s", MODULE_NID, modulesStateNode);
        final DataContainerChild node = moduleListNode.get();
        checkState(node instanceof MapNode, "Unexpected structure for container: %s in : %s. Expecting a list",
            MODULE_NID, modulesStateNode);

        final MapNode moduleList = (MapNode) node;
        final Collection<MapEntryNode> modules = moduleList.body();
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
    public LibraryModulesSchemas create(final String url) {
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

    private static Optional<DataContainerChild> findModulesStateNode(final NormalizedNode result,
            final EffectiveModelContext context) {
        if (result == null) {
            return Optional.empty();
        }
        // FIXME: unchecked cast
        final var rpcResultOpt = ((ContainerNode) result).findChildByArg(NETCONF_DATA_NODEID);
        if (rpcResultOpt.isEmpty()) {
            return Optional.empty();
        }

        final var rpcResult = rpcResultOpt.get();
        verify(rpcResult instanceof DOMSourceAnyxmlNode, "Unexpected result %s", rpcResult);

        // Server may include additional data which we do not understand. Make sure we trim the input before we try
        // to interpret it.
        // FIXME: this is something NetconfUtil.transformDOMSourceToNormalizedNode(), and more generally, NormalizedNode
        //        codecs should handle. We really want to a NormalizedNode tree which can be directly queried for known
        //        things while completely ignoring XML content (and hence its semantics) of other elements.
        final var filteredBody = NetconfStateSchemas.ietfMonitoringCopy(((DOMSourceAnyxmlNode) rpcResult).body());

        final NormalizedNode dataNode;
        try {
            dataNode = NetconfUtil.transformDOMSourceToNormalizedNode(context, filteredBody).getResult();
        } catch (XMLStreamException | URISyntaxException | IOException | SAXException e) {
            LOG.warn("Failed to transform {}", rpcResult, e);
            return Optional.empty();
        }

        return ((DataContainerNode) dataNode).findChildByArg(MODULES_STATE_NID);
    }

    private LibraryModulesSchemas createFromURLConnection(final URLConnection connection) {
        String contentType = connection.getContentType();

        // TODO try to guess Json also from intput stream
        if (guessJsonFromFileName(connection.getURL().getFile())) {
            contentType = "application/json";
        }

        requireNonNull(contentType, "Content type unknown");
        checkState(contentType.equals("application/json") || contentType.equals("application/xml"),
                "Only XML and JSON types are supported.");

        Optional<NormalizedNode> optionalModulesStateNode = Optional.empty();
        try (InputStream in = connection.getInputStream()) {
            optionalModulesStateNode = contentType.equals("application/json") ? readJson(in) : readXml(in);
        } catch (final IOException e) {
            LOG.warn("Unable to download yang library from {}", connection.getURL(), e);
        }

        if (optionalModulesStateNode.isEmpty()) {
            return new LibraryModulesSchemas(ImmutableMap.of());
        }

        final NormalizedNode modulesStateNode = optionalModulesStateNode.get();
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

    private Optional<NormalizedNode> readJson(final InputStream in) {
        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        final JsonParserStream jsonParser = JsonParserStream.create(writer, jsonCodecs);
        final JsonReader reader = new JsonReader(new InputStreamReader(in, Charset.defaultCharset()));

        jsonParser.parse(reader);

        return resultHolder.isFinished() ? Optional.of(resultHolder.getResult()) : Optional.empty();
    }

    private Optional<NormalizedNode> readXml(final InputStream in) {
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
            final XmlParserStream xmlParser = XmlParserStream.create(writer,
                SchemaInferenceStack.ofDataTreePath(libraryContext, ModulesState.QNAME).toInference());
            xmlParser.traverse(new DOMSource(doc.getDocumentElement()));
            return Optional.of(resultHolder.getResult());
        } catch (XMLStreamException | URISyntaxException | IOException | SAXException e) {
            LOG.warn("Unable to parse yang library xml content", e);
        }

        return Optional.empty();
    }

    private static @Nullable Entry<QName, URL> createFromEntry(final MapEntryNode moduleNode) {
        final QName moduleNodeId = moduleNode.getIdentifier().getNodeType();
        checkArgument(moduleNodeId.equals(Module.QNAME), "Wrong QName %s", moduleNodeId);

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
                : QName.create(XMLNamespace.of(moduleNameSpace), moduleName);

        try {
            return new SimpleImmutableEntry<>(moduleQName, new URL(schemaUriAsString.get()));
        } catch (final MalformedURLException e) {
            LOG.warn("Skipping library schema for {}. URL {} representing yang schema resource is not valid",
                    moduleNode, schemaUriAsString.get());
            return null;
        }
    }

    private static Optional<String> getSingleChildNodeValue(final DataContainerNode schemaNode,
                                                            final NodeIdentifier childNodeId) {
        final Optional<DataContainerChild> node = schemaNode.findChildByArg(childNodeId);
        checkArgument(node.isPresent(), "Child node %s not present", childNodeId.getNodeType());
        return getValueOfSimpleNode(node.get());
    }

    private static Optional<String> getValueOfSimpleNode(final NormalizedNode node) {
        final String valueStr = node.body().toString();
        return Strings.isNullOrEmpty(valueStr) ? Optional.empty() : Optional.of(valueStr.trim());
    }
}
