/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_DATA_NODEID;
import static org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.YangModuleInfoImpl.qnameOf;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.client.mdsal.api.NetconfRpcService;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.ProvidedSources;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.util.NormalizedDataUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.get.input.Filter;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema.Location;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
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
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.api.stmt.FeatureSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;
import org.xml.sax.SAXException;

/**
 * Default implementation resolving schemas QNames from netconf-state or from modules-state.
 */
public final class NetconfStateSchemasResolverImpl implements NetconfDeviceSchemasResolver {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfStateSchemasResolverImpl.class);
    private static final String MONITORING_NAMESPACE = NetconfState.QNAME.getNamespace().toString();
    private static final @NonNull NodeIdentifier SCHEMA_FORMAT_NODEID = NodeIdentifier.create(qnameOf("format"));
    private static final @NonNull NodeIdentifier SCHEMA_LOCATION_NODEID = NodeIdentifier.create(qnameOf("location"));
    private static final @NonNull NodeIdentifier SCHEMA_NAMESPACE_NODEID = NodeIdentifier.create(qnameOf("namespace"));
    private static final @NonNull NodeIdentifier SCHEMA_IDENTIFIER_NODEID =
        NodeIdentifier.create(qnameOf("identifier"));
    private static final @NonNull NodeIdentifier SCHEMA_VERSION_NODEID = NodeIdentifier.create(qnameOf("version"));
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

        GET_SCHEMAS_RPC = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(GetInput.QNAME))
            .withChild(ImmutableNodes.newAnyxmlBuilder(DOMSource.class)
                .withNodeIdentifier(new NodeIdentifier(Filter.QNAME))
                .withValue(new DOMSource(filterElem))
                .build())
            .build();
    }

    @Override
    public ListenableFuture<NetconfDeviceSchemas> resolve(final RemoteDeviceId deviceId,
            final NetconfSessionPreferences sessionPreferences, final NetconfRpcService deviceRpc,
            final EffectiveModelContext baseModelContext) {
        // Find all schema sources provided via ietf-netconf-monitoring, if supported
        final var monitoringFuture = sessionPreferences.isMonitoringSupported()
            ? resolveMonitoringSources(deviceId, deviceRpc, baseModelContext)
                : Futures.immediateFuture(List.<ProvidedSources<?>>of());

        final AsyncFunction<List<ProvidedSources<?>>, NetconfDeviceSchemas> function;
        LOG.debug("{}: resolving YANG 1.0 conformance", deviceId);
        function = sources -> resolveYang10(deviceId, sessionPreferences, deviceRpc, baseModelContext, sources);

        // FIXME: check for
        //            urn:ietf:params:netconf:capability:yang-library:1.0?revision=<date>&module-set-id=<id>
        //
        //        and then dispatch to resolveYang11(), which should resolve schemas based on RFC7950's conformance
        //        announcement via <hello/> message, as defined in
        //        https://www.rfc-editor.org/rfc/rfc7950#section-5.6.4
        //
        //        if (sessionPreferences.containsNonModuleCapability(CapabilityURN.YANG_LIBRARY)) {
        //            LOG.debug("{}: resolving YANG 1.1 conformance", deviceId);
        //            function = sources -> resolveYang11(deviceId, sessionPreferences, deviceRpc, baseModelContext,
        //                sources);
        //        }

        // FIXME: check for
        //            urn:ietf:params:netconf:capability:yang-library:1.1?revision=<date>&content-id=<content-id-value>
        //        where date is at least 2019-01-04
        //
        //        and then dispatch to resolveNmda():
        //
        //        if (sessionPreferences.containsNonModuleCapability(CapabilityURN.YANG_LIBRARY)) {
        //            LOG.debug("{}: resolving YANG 1.1 NMDA conformance", deviceId);
        //            function = sources -> resolveNmda(deviceId, sessionPreferences, deviceRpc, baseModelContext,
        //                sources);
        //        }

        return Futures.transformAsync(monitoringFuture, function, MoreExecutors.directExecutor());
    }

    private static ListenableFuture<List<ProvidedSources<?>>> resolveMonitoringSources(
            final RemoteDeviceId deviceId, final NetconfRpcService deviceRpc,
            final EffectiveModelContext baseModelContext) {
        return Futures.transform(deviceRpc.invokeNetconf(Get.QNAME, GET_SCHEMAS_RPC),
            result -> resolveMonitoringSources(deviceId, deviceRpc, result, baseModelContext),
            MoreExecutors.directExecutor());
    }

    private static List<ProvidedSources<?>> resolveMonitoringSources(final RemoteDeviceId deviceId,
            final NetconfRpcService deviceRpc, final DOMRpcResult rpcResult,
            final EffectiveModelContext baseModelContext) {
        // Two-pass error reporting: first check if there is a hard error, then log any remaining warnings
        final var errors = rpcResult.errors();
        if (errors.stream().anyMatch(error -> error.getSeverity() == ErrorSeverity.ERROR)) {
            LOG.warn("{}: failed to get netconf-state", errors);
            return List.of();
        }
        for (var error : errors) {
            LOG.info("{}: schema retrieval warning: {}", deviceId, error);
        }

        final var rpcOutput = rpcResult.value();
        if (rpcOutput == null) {
            LOG.warn("{}: missing RPC output", deviceId);
            return List.of();
        }
        final var data = rpcOutput.childByArg(NETCONF_DATA_NODEID);
        if (data == null) {
            LOG.warn("{}: missing RPC data", deviceId);
            return List.of();
        }
        if (!(data instanceof AnyxmlNode<?> anyxmlData)) {
            LOG.warn("{}: unexpected data {}", deviceId, data.prettyTree());
            return List.of();
        }
        final var dataBody = anyxmlData.body();
        if (!(dataBody instanceof DOMSource domDataBody)) {
            LOG.warn("{}: unexpected body {}", deviceId, dataBody);
            return List.of();
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
            normalizedData = NormalizedDataUtil.transformDOMSourceToNormalizedNode(baseModelContext, filteredBody)
                .getResult().data();
        } catch (XMLStreamException | URISyntaxException | IOException | SAXException e) {
            LOG.warn("{}: failed to transform {}", deviceId, filteredBody, e);
            return List.of();
        }

        // The result should be the root of datastore, hence a DataContainerNode
        if (!(normalizedData instanceof DataContainerNode root)) {
            LOG.warn("{}: unexpected normalized data {}", deviceId, normalizedData.prettyTree());
            return List.of();
        }

        // container netconf-state
        final var netconfState = root.childByArg(new NodeIdentifier(NetconfState.QNAME));
        if (netconfState == null) {
            LOG.warn("{}: missing netconf-state", deviceId);
            return List.of();
        }
        if (!(netconfState instanceof ContainerNode netconfStateCont)) {
            LOG.warn("{}: unexpected netconf-state {}", deviceId, netconfState.prettyTree());
            return List.of();
        }

        // container schemas
        final var schemas = netconfStateCont.childByArg(new NodeIdentifier(Schemas.QNAME));
        if (schemas == null) {
            LOG.warn("{}: missing schemas", deviceId);
            return List.of();
        }
        if (!(schemas instanceof ContainerNode schemasNode)) {
            LOG.warn("{}: unexpected schemas {}", deviceId, schemas.prettyTree());
            return List.of();
        }

        return resolveMonitoringSources(deviceId, deviceRpc, schemasNode);
    }

    /**
     * Parse response of get(netconf-state/schemas) to find all schemas under netconf-state/schemas.
     */
    @VisibleForTesting
    static List<ProvidedSources<?>> resolveMonitoringSources(final RemoteDeviceId deviceId,
            final NetconfRpcService deviceRpc, final ContainerNode schemasNode) {
        final var child = schemasNode.childByArg(new NodeIdentifier(Schema.QNAME));
        if (child == null) {
            LOG.warn("{}: missing schema", deviceId);
            return List.of();
        }
        if (!(child instanceof SystemMapNode schemaMap)) {
            LOG.warn("{}: unexpected schema {}", deviceId, child.prettyTree());
            return List.of();
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
            final var qname = createFromNormalizedNode(deviceId, schemaNode);
            if (qname != null) {
                builder.add(qname);
            }
        }

        final var sources = builder.build();
        return sources.isEmpty() ? List.of() : List.of(new ProvidedSources<>(YangTextSource.class,
            new MonitoringSchemaSourceProvider(deviceId, deviceRpc), sources));
    }

    private static @Nullable QName createFromNormalizedNode(final RemoteDeviceId id,
            final MapEntryNode schemaEntry) {
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
            LOG.debug("{}: Ignoring schema due to no NETCONF location", id);
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

    // Resolve schemas based on RFC6020's conformance announcement in <hello/> message, as defined in
    // https://www.rfc-editor.org/rfc/rfc6020#section-5.6.4
    private static ListenableFuture<NetconfDeviceSchemas> resolveYang10(final RemoteDeviceId deviceId,
            final NetconfSessionPreferences sessionPreferences, final NetconfRpcService deviceRpc,
            final EffectiveModelContext baseModelContext, final List<ProvidedSources<?>> monitoringSources) {
        final var providedSources = monitoringSources.stream()
            .flatMap(sources -> sources.sources().stream())
            .collect(Collectors.toSet());
        LOG.debug("{}: Schemas exposed by ietf-netconf-monitoring: {}", deviceId, providedSources);

        final var requiredSources = new HashSet<>(sessionPreferences.moduleBasedCaps().keySet());
        final var requiredSourcesNotProvided = Sets.difference(requiredSources, providedSources);
        if (!requiredSourcesNotProvided.isEmpty()) {
            LOG.warn("{}: Netconf device does not provide all yang models reported in hello message capabilities,"
                    + " required but not provided: {}", deviceId, requiredSourcesNotProvided);
            LOG.warn("{}: Attempting to build schema context from required sources", deviceId);
        }

        // Here all the sources reported in netconf monitoring are merged with those reported in hello.
        // It is necessary to perform this since submodules are not mentioned in hello but still required.
        // This clashes with the option of a user to specify supported yang models manually in configuration
        // for netconf-connector and as a result one is not able to fully override yang models of a device.
        // It is only possible to add additional models.
        final var providedSourcesNotRequired = Sets.difference(providedSources, requiredSources);
        if (!providedSourcesNotRequired.isEmpty()) {
            LOG.warn("{}: Netconf device provides additional yang models not reported in "
                    + "hello message capabilities: {}", deviceId, providedSourcesNotRequired);
            LOG.warn("{}: Adding provided but not required sources as required to prevent failures", deviceId);
            LOG.debug("{}: Netconf device reported in hello: {}", deviceId, requiredSources);
            requiredSources.addAll(providedSourcesNotRequired);
        }


        return Futures.immediateFuture(new NetconfDeviceSchemas(requiredSources,
            // FIXME: determine features
            FeatureSet.builder().build(),
            // FIXME: use this instead of adjusted required sources
            Set.of(),
            monitoringSources));
    }
}
