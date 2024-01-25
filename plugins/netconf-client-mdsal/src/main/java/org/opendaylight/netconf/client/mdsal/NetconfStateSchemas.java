/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.IETF_NETCONF_MONITORING;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_DATA_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_FILTER_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_QNAME;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.format.DateTimeParseException;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.common.mdsal.NormalizedDataUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema.Location;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;
import org.xml.sax.SAXException;

/**
 * Holds QNames for all yang modules reported by ietf-netconf-monitoring/state/schemas.
 */
public final class NetconfStateSchemas implements NetconfDeviceSchemas {
    public static final NetconfStateSchemas EMPTY = new NetconfStateSchemas(ImmutableSet.of());

    private static final Logger LOG = LoggerFactory.getLogger(NetconfStateSchemas.class);
    private static final String MONITORING_NAMESPACE = IETF_NETCONF_MONITORING.getNamespace().toString();
    private static final @NonNull NodeIdentifier SCHEMA_FORMAT_NODEID =
        NodeIdentifier.create(QName.create(IETF_NETCONF_MONITORING, "format").intern());
    private static final @NonNull NodeIdentifier SCHEMA_LOCATION_NODEID =
        NodeIdentifier.create(QName.create(IETF_NETCONF_MONITORING, "location").intern());
    private static final @NonNull NodeIdentifier SCHEMA_NAMESPACE_NODEID =
        NodeIdentifier.create(QName.create(IETF_NETCONF_MONITORING, "namespace").intern());
    private static final @NonNull NodeIdentifier SCHEMA_IDENTIFIER_NODEID =
        NodeIdentifier.create(QName.create(IETF_NETCONF_MONITORING, "identifier").intern());
    private static final @NonNull NodeIdentifier SCHEMA_VERSION_NODEID =
        NodeIdentifier.create(QName.create(IETF_NETCONF_MONITORING, "version").intern());
    private static final @NonNull String NETCONF_LOCATION = Location.Enumeration.NETCONF.getName();
    private static final @NonNull ContainerNode GET_SCHEMAS_RPC;

    static {
        final var document = XmlUtil.newDocument();
        final var filterElem = document.createElementNS(NamespaceURN.BASE, "filter");
        filterElem.setAttribute("type", "subtree");

        final var stateElem = document.createElementNS(NetconfState.QNAME.getNamespace().toString(),
            NetconfState.QNAME.getLocalName());
        stateElem.appendChild(document.createElementNS(Schemas.QNAME.getNamespace().toString(),
            Schemas.QNAME.getLocalName()));
        filterElem.appendChild(stateElem);

        GET_SCHEMAS_RPC = Builders.containerBuilder()
                .withNodeIdentifier(NETCONF_GET_NODEID)
                .withChild(Builders.anyXmlBuilder()
                    .withNodeIdentifier(NETCONF_FILTER_NODEID)
                    .withValue(new DOMSource(filterElem))
                    .build())
                .build();
    }

    private final ImmutableSet<QName> availableYangSchemasQNames;

    public NetconfStateSchemas(final Set<QName> availableYangSchemasQNames) {
        this.availableYangSchemasQNames = ImmutableSet.copyOf(availableYangSchemasQNames);
    }

    @Override
    public Set<QName> getAvailableYangSchemasQNames() {
        return availableYangSchemasQNames;
    }

    /**
     * Issue get request to remote device and parse response to find all schemas under netconf-state/schemas.
     */
    static ListenableFuture<NetconfStateSchemas> forDevice(final DOMRpcService deviceRpc,
            final NetconfSessionPreferences remoteSessionCapabilities, final RemoteDeviceId id,
            final EffectiveModelContext modelContext) {
        if (!remoteSessionCapabilities.isMonitoringSupported()) {
            // TODO - need to search for get-schema support, not just ietf-netconf-monitoring support
            // issue might be a deviation to ietf-netconf-monitoring where get-schema is unsupported...
            LOG.warn("{}: Netconf monitoring not supported on device, cannot detect provided schemas", id);
            return Futures.immediateFuture(EMPTY);
        }

        final var future = SettableFuture.<NetconfStateSchemas>create();
        Futures.addCallback(deviceRpc.invokeRpc(NETCONF_GET_QNAME, GET_SCHEMAS_RPC),
            new FutureCallback<DOMRpcResult>() {
                @Override
                public void onSuccess(final DOMRpcResult result) {
                    onGetSchemasResult(future, id, modelContext, result);
                }

                @Override
                public void onFailure(final Throwable cause) {
                    // debug, because we expect this error to be reported by caller
                    LOG.debug("{}: Unable to detect available schemas", id, cause);
                    future.setException(cause);
                }
            }, MoreExecutors.directExecutor());
        return future;
    }

    private static void onGetSchemasResult(final SettableFuture<NetconfStateSchemas> future, final RemoteDeviceId id,
            final EffectiveModelContext modelContext, final DOMRpcResult result) {
        // Two-pass error reporting: first check if there is a hard error, then log any remaining warnings
        final var errors = result.errors();
        if (errors.stream().anyMatch(error -> error.getSeverity() == ErrorSeverity.ERROR)) {
            // FIXME: a good exception, which can report the contents of errors?
            future.setException(new OperationFailedException("Failed to get netconf-state", errors));
            return;
        }
        for (var error : errors) {
            LOG.info("{}: schema retrieval warning: {}", id, error);
        }

        final var value = result.value();
        if (value == null) {
            LOG.warn("{}: missing RPC output", id);
            future.set(EMPTY);
            return;
        }
        final var data = value.childByArg(NETCONF_DATA_NODEID);
        if (data == null) {
            LOG.warn("{}: missing RPC data", id);
            future.set(EMPTY);
            return;
        }
        if (!(data instanceof AnyxmlNode<?> anyxmlData)) {
            future.setException(new VerifyException("Unexpected data " + data.prettyTree()));
            return;
        }
        final var dataBody = anyxmlData.body();
        if (!(dataBody instanceof DOMSource domDataBody)) {
            future.setException(new VerifyException("Unexpected body " + dataBody));
            return;
        }

        // Server may include additional data which we do not understand. Make sure we trim the input before we try
        // to interpret it.
        // FIXME: we should not be going to NormalizedNode at all. We are interpreting a very limited set of data
        //        in the context of setting up the normalization schema. Everything we are dealing with are plain
        //        strings for which yang-common provides everything we need -- with the notable exception of identityref
        //        values. Those boil down into plain QNames -- so we can talk to XmlCodecs.identityRefCodec(). That
        //        operation needs to also handle IAE and ignore unknown values.
        final var filteredBody = ietfMonitoringCopy(domDataBody);

        // Now normalize the anyxml content to the selected model context
        final NormalizedNode normalizedData;
        try {
            normalizedData = NormalizedDataUtil.transformDOMSourceToNormalizedNode(modelContext, filteredBody)
                .getResult().data();
        } catch (XMLStreamException | URISyntaxException | IOException | SAXException e) {
            LOG.debug("{}: failed to transform {}", filteredBody, e);
            future.setException(e);
            return;
        }

        // The result should be the root of datastore, hence a DataContainerNode
        if (!(normalizedData instanceof DataContainerNode root)) {
            future.setException(new VerifyException("Unexpected normalized data " + normalizedData.prettyTree()));
            return;
        }

        // container netconf-state
        final var netconfState = root.childByArg(new NodeIdentifier(NetconfState.QNAME));
        if (netconfState == null) {
            LOG.warn("{}: missing netconf-state", id);
            future.set(EMPTY);
            return;
        }
        if (!(netconfState instanceof ContainerNode netconfStateCont)) {
            future.setException(new VerifyException("Unexpected netconf-state " + netconfState.prettyTree()));
            return;
        }

        // container schemas
        final var schemas = netconfStateCont.childByArg(new NodeIdentifier(Schemas.QNAME));
        if (schemas == null) {
            LOG.warn("{}: missing schemas", id);
            future.set(EMPTY);
            return;
        }
        if (!(schemas instanceof ContainerNode schemasNode)) {
            future.setException(new VerifyException("Unexpected schemas " + schemas.prettyTree()));
            return;
        }

        create(future, id, schemasNode);
    }

    /**
     * Parse response of get(netconf-state/schemas) to find all schemas under netconf-state/schemas.
     */
    @VisibleForTesting
    static void create(final SettableFuture<NetconfStateSchemas> future, final RemoteDeviceId id,
            final ContainerNode schemasNode) {
        final var child = schemasNode.childByArg(new NodeIdentifier(Schema.QNAME));
        if (child == null) {
            LOG.warn("{}: missing schema", id);
            future.set(EMPTY);
            return;
        }
        if (!(child instanceof SystemMapNode schemaMap)) {
            future.setException(new VerifyException("Unexpected schemas " + child.prettyTree()));
            return;
        }

        // FIXME: we are producing the wrong thing here and simply not handling all the use cases
        //        - instead of QName we want to say 'SourceIdentifier and XMLNamespace', because these are source files
        //          and there is some namespace guidance -- which we do not really need (because localName+revision is
        //          guaranteed to be unique and hence there cannot be a conflict on submodule names
        //        - we handle on "NETCONF" and completely ignore the URI case -- which is something useful for
        //          offloading model discovery
        //
        //        At the end of the day, all this information is going into yang-parser-impl, i.e. it will need to go
        //        through SchemaSource and all the yang-repo-{api,spi} stuff. That implies policy and further control
        //        point which needs to be customizable as we want to plug in various providers and differing policies.
        //
        //        A few examples:
        //        - all URIs need to be resolved, which needs pluggable resolvers (https:// is obvious, but xri:// needs
        //          to hand this off to a dedicated resolver
        //        - we do not want to use URI.toURL().openConnection(), but leave it up to policy -- for example one
        //          would want to use java.net.http.HttpClient, which means authentication and content negotiation.
        //          Content negotiation is needed to establish byte stream encoding, plus
        //        - all sources of schema are subject to caching, perhaps even in IRSource form
        //
        //        At the end of the day, we should just produce an instance of Schema.class and let others deal with
        //        translating it to the real world -- for example turning a String into a XMLNamespace or a local name.
        final var builder = ImmutableSet.<QName>builderWithExpectedSize(schemaMap.size());
        for (var schemaNode : schemaMap.body()) {
            final var qname = createFromNormalizedNode(id, schemaNode);
            if (qname != null) {
                builder.add(qname);
            }
        }
        future.set(new NetconfStateSchemas(builder.build()));
    }

    private static @Nullable QName createFromNormalizedNode(final RemoteDeviceId id, final MapEntryNode schemaEntry) {
        // These three are mandatory due to 'key "identifier version format"'
        final var format = schemaEntry.getChildByArg(SCHEMA_FORMAT_NODEID).body();
        // FIXME: we should support Yin as well
        if (!Yang.QNAME.equals(format)) {
            LOG.debug("{}: Ignoring schema due to unsupported format: {}", id, format);
            return null;
        }
        // Note: module name or submodule name
        final var identifier = (String) schemaEntry.getChildByArg(SCHEMA_IDENTIFIER_NODEID).body();
        // Note: revision
        final var version = (String) schemaEntry.getChildByArg(SCHEMA_VERSION_NODEID).body();

        // FIXME: we should be able to promote to 'getChildByArg()', IFF the normalizer is enforcing mandatory nodes
        @SuppressWarnings("unchecked")
        final var namespaceLeaf = (LeafNode<String>) schemaEntry.childByArg(SCHEMA_NAMESPACE_NODEID);
        if (namespaceLeaf == null) {
            LOG.warn("{}: Ignoring schema due to missing namespace", id);
            return null;
        }

        @SuppressWarnings("unchecked")
        final var location = (SystemLeafSetNode<String>) schemaEntry.childByArg(SCHEMA_LOCATION_NODEID);
        if (location == null) {
            LOG.debug("{}: Ignoring schema due to missing location", id);
            return null;
        }

        boolean foundNetconf = false;
        for (var locEntry : location.body()) {
            final var loc = locEntry.body();
            if (NETCONF_LOCATION.equals(loc)) {
                foundNetconf = true;
                break;
            }

            // FIXME: the other string is an Uri, we should be exposing that as well
            LOG.debug("{}: Ignoring schema due to unsupported location: {}", id, loc);
        }

        if (!foundNetconf) {
            LOG.debug("{}: Ignoring schema due to no NETCONF location: {}", id);
            return null;
        }

        try {
            final var namespace = XMLNamespace.of(namespaceLeaf.body());
            final var revision = version.isEmpty() ? null : Revision.of(version);
            return QName.create(namespace, revision, identifier);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            LOG.warn("{}: Ignoring malformed schema {}", id, schemaEntry.prettyTree(), e);
            return null;
        }
    }

    @VisibleForTesting
    static DOMSource ietfMonitoringCopy(final DOMSource domSource) {
        final var sourceDoc = XmlUtil.newDocument();
        sourceDoc.appendChild(sourceDoc.importNode(domSource.getNode(), true));

        final var treeWalker = ((DocumentTraversal) sourceDoc).createTreeWalker(sourceDoc.getDocumentElement(),
            NodeFilter.SHOW_ALL, node -> {
                final var namespace = node.getNamespaceURI();
                return namespace == null || MONITORING_NAMESPACE.equals(namespace)
                    ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
            }, false);

        final var filteredDoc = XmlUtil.newDocument();
        filteredDoc.appendChild(filteredDoc.importNode(treeWalker.getRoot(), false));
        final var filteredElement = filteredDoc.getDocumentElement();
        copyChildren(treeWalker, filteredDoc, filteredElement);

        return new DOMSource(filteredElement);
    }

    private static void copyChildren(final TreeWalker walker, final Document targetDoc, final Node targetNode) {
        if (walker.firstChild() != null) {
            for (var node = walker.getCurrentNode(); node != null; node = walker.nextSibling()) {
                final var importedNode = targetDoc.importNode(node, false);
                targetNode.appendChild(importedNode);
                copyChildren(walker, targetDoc, importedNode);
                walker.setCurrentNode(node);
            }
        }
    }
}
