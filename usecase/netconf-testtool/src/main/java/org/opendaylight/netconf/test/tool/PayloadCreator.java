/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencrypted;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.svc.v1.urn.opendaylight.netconf.node.topology.rev240911.YangModuleInfoImpl;
import org.opendaylight.yangtools.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PayloadCreator {
    private static final Logger LOG = LoggerFactory.getLogger(PayloadCreator.class);
    private static final EffectiveModelContext NETWORK_TOPOLOGY_SCHEMA_CONTEXT =
        BindingRuntimeHelpers.createEffectiveModel(List.of(YangModuleInfoImpl.getInstance()));
    private static final JSONCodecFactory NETWORK_TOPOLOGY_JSON_CODEC_FACTORY =
        JSONCodecFactorySupplier.RFC7951.getShared(NETWORK_TOPOLOGY_SCHEMA_CONTEXT);

    private static final QName TOPOLOGY_ID_QNAME = QName.create(Topology.QNAME, "topology-id").intern();
    private static final QName NODE_ID_QNAME = QName.create(Node.QNAME, "node-id").intern();

    private static final NodeIdentifier NETCONF_NODE_IDENTIFIER =
        NodeIdentifier.create(YangModuleInfoImpl.qnameOf("netconf-node"));
    private static final NodeIdentifier PORT_NODE_IDENTIFIER =
        NodeIdentifier.create(YangModuleInfoImpl.qnameOf("port"));
    private static final NodeIdentifier HOST_NODE_IDENTIFIER =
        NodeIdentifier.create(YangModuleInfoImpl.qnameOf("host"));
    private static final NodeIdentifier USERNAME_NODE_IDENTIFIER =
        NodeIdentifier.create(YangModuleInfoImpl.qnameOf("username"));
    private static final NodeIdentifier PASSWORD_NODE_IDENTIFIER =
        NodeIdentifier.create(YangModuleInfoImpl.qnameOf("password"));
    private static final NodeIdentifier CREDENTIALS_NODE_IDENTIFIER =
        // Note: this is an instantiated container, we need to use the proper namespace
        NodeIdentifier.create(Credentials.QNAME.bindTo(PORT_NODE_IDENTIFIER.getNodeType().getModule()).intern());
    private static final NodeIdentifier LOGIN_PASSWORD_UNENCRYPTED_NODE_IDENTIFIER =
        // Note: this is an instantiated container, we need to use the proper namespace
        NodeIdentifier.create(LoginPasswordUnencrypted.QNAME.bindTo(
            PORT_NODE_IDENTIFIER.getNodeType().getModule()).intern());
    private static final NodeIdentifier TCP_ONLY_NODE_IDENTIFIER =
        NodeIdentifier.create(YangModuleInfoImpl.qnameOf("tcp-only"));
    private static final NodeIdentifier KEEPALIVE_DELAY_NODE_IDENTIFIER =
        NodeIdentifier.create(YangModuleInfoImpl.qnameOf("keepalive-delay"));
    private static final NodeIdentifier SCHEMALESS_NODE_IDENTIFIER =
        NodeIdentifier.create(YangModuleInfoImpl.qnameOf("schemaless"));
    private static final String DEFAULT_TOPOLOGY_ID = "topology-netconf";
    private static final String DEFAULT_NODE_PASSWORD = "admin";
    private static final String DEFAULT_NODE_USERNAME = "admin";

    private static final boolean DEFAULT_NODE_SCHEMALESS = false;
    private static final int DEFAULT_NODE_KEEPALIVE_DELAY = 0;
    private static final int DEFAULT_REQUEST_PAYLOAD_INDENTATION = 2;

    private PayloadCreator() {
        // hidden on purpose
    }

    static String createStringPayload(final List<Integer> devices, final TesttoolParameters parameters) {
        return normalizedNodeToString(createNormalizedNodePayload(devices, parameters));
    }

    private static String normalizedNodeToString(final SystemMapNode node) {
        final StringWriter writer = new StringWriter();
        final JsonWriter jsonWriter = JsonWriterFactory.createJsonWriter(writer, DEFAULT_REQUEST_PAYLOAD_INDENTATION);
        final NormalizedNodeStreamWriter jsonStream = JSONNormalizedNodeStreamWriter.createExclusiveWriter(
                NETWORK_TOPOLOGY_JSON_CODEC_FACTORY, Absolute.of(NetworkTopology.QNAME), null, jsonWriter);
        try (NormalizedNodeWriter nodeWriter = NormalizedNodeWriter.forStreamWriter(jsonStream)) {
            nodeWriter.write(node);
        } catch (final IOException e) {
            LOG.error("Failed to serialize node: {} to JSON", node, e);
            throw new IllegalStateException("Failed to serialize node to JSON", e);
        }
        return writer.toString();
    }

    private static SystemMapNode createNormalizedNodePayload(final List<Integer> devices,
            final TesttoolParameters parameters) {
        final var nodeBuilder = ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(NodeIdentifier.create(Node.QNAME));
        for (final Integer device : devices) {
            nodeBuilder.withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(Node.QNAME, NODE_ID_QNAME, device + "-sim-device"))
                .withChild(ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier((NETCONF_NODE_IDENTIFIER))
                    .withChild(ImmutableNodes.leafNode(PORT_NODE_IDENTIFIER, Uint16.valueOf(device)))
                    .withChild(ImmutableNodes.leafNode(HOST_NODE_IDENTIFIER, parameters.generateConfigsAddress))
                    .withChild(ImmutableNodes.newChoiceBuilder()
                        .withNodeIdentifier(CREDENTIALS_NODE_IDENTIFIER)
                        .withChild(ImmutableNodes.newContainerBuilder()
                            .withNodeIdentifier(LOGIN_PASSWORD_UNENCRYPTED_NODE_IDENTIFIER)
                            .withChild(ImmutableNodes.leafNode(USERNAME_NODE_IDENTIFIER, DEFAULT_NODE_USERNAME))
                            .withChild(ImmutableNodes.leafNode(PASSWORD_NODE_IDENTIFIER, DEFAULT_NODE_PASSWORD))
                            .build())
                        .build())
                    .withChild(ImmutableNodes.leafNode(TCP_ONLY_NODE_IDENTIFIER, !parameters.ssh))
                    .withChild(ImmutableNodes.leafNode(KEEPALIVE_DELAY_NODE_IDENTIFIER, DEFAULT_NODE_KEEPALIVE_DELAY))
                    .withChild(ImmutableNodes.leafNode(SCHEMALESS_NODE_IDENTIFIER, DEFAULT_NODE_SCHEMALESS))
                    .build())
                .build());
        }

        return ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(NodeIdentifier.create(Topology.QNAME))
            .withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(Topology.QNAME,
                    TOPOLOGY_ID_QNAME, DEFAULT_TOPOLOGY_ID))
                .withChild(nodeBuilder.build())
                .build())
            .build();
    }
}
