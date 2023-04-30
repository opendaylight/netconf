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
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeFields;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PayloadCreator {
    private static final Logger LOG = LoggerFactory.getLogger(PayloadCreator.class);
    private static final EffectiveModelContext NETWORK_TOPOLOGY_SCHEMA_CONTEXT =
        BindingRuntimeHelpers.createEffectiveModel(List.of($YangModuleInfoImpl.getInstance()));
    private static final JSONCodecFactory NETWORK_TOPOLOGY_JSON_CODEC_FACTORY =
        JSONCodecFactorySupplier.RFC7951.getShared(NETWORK_TOPOLOGY_SCHEMA_CONTEXT);

    private static final QName TOPOLOGY_ID_QNAME = QName.create(Topology.QNAME, "topology-id").intern();
    private static final QName NODE_ID_QNAME = QName.create(Node.QNAME, "node-id").intern();

    private static final NodeIdentifier PORT_NODE_IDENTIFIER =
        NodeIdentifier.create(QName.create(NetconfNodeFields.QNAME, "port").intern());
    private static final NodeIdentifier HOST_NODE_IDENTIFIER =
        NodeIdentifier.create(QName.create(NetconfNodeFields.QNAME,"host").intern());
    private static final NodeIdentifier USERNAME_NODE_IDENTIFIER =
        NodeIdentifier.create(QName.create(NetconfNodeFields.QNAME,"username").intern());
    private static final NodeIdentifier PASSWORD_NODE_IDENTIFIER =
        NodeIdentifier.create(QName.create(NetconfNodeFields.QNAME, "password").intern());
    private static final NodeIdentifier CREDENTIALS_NODE_IDENTIFIER =
        // Note: this is an instantiated container, we need to use the proper namespace
        NodeIdentifier.create(Credentials.QNAME.bindTo(NetconfNodeFields.QNAME.getModule()).intern());
    private static final NodeIdentifier TCP_ONLY_NODE_IDENTIFIER =
        NodeIdentifier.create(QName.create(NetconfNodeFields.QNAME, "tcp-only").intern());
    private static final NodeIdentifier KEEPALIVE_DELAY_NODE_IDENTIFIER =
        NodeIdentifier.create(QName.create(NetconfNodeFields.QNAME, "keepalive-delay").intern());
    private static final NodeIdentifier SCHEMALESS_NODE_IDENTIFIER =
        NodeIdentifier.create(QName.create(NetconfNodeFields.QNAME, "schemaless").intern());
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

    private static String normalizedNodeToString(final NormalizedNode node) {
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

    private static NormalizedNode createNormalizedNodePayload(final List<Integer> devices,
            final TesttoolParameters parameters) {
        final var nodeBuilder = Builders.mapBuilder().withNodeIdentifier(NodeIdentifier.create(Node.QNAME));
        for (final Integer device : devices) {
            nodeBuilder.withChild(Builders.mapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(Node.QNAME, NODE_ID_QNAME,
                            createNodeID(device)))
                    .withChild(leafPort(device))
                    .withChild(leafHost(parameters.generateConfigsAddress))
                    .withChild(containerCredentials(DEFAULT_NODE_USERNAME, DEFAULT_NODE_PASSWORD))
                    .withChild(leafTcpOnly(!parameters.ssh))
                    .withChild(leafKeepaliveDelay(DEFAULT_NODE_KEEPALIVE_DELAY))
                    .withChild(leafSchemaless(DEFAULT_NODE_SCHEMALESS))
                    .build());
        }

        return Builders.mapBuilder()
                .withNodeIdentifier(NodeIdentifier.create(Topology.QNAME))
                .withChild(Builders.mapEntryBuilder().withNodeIdentifier(NodeIdentifierWithPredicates
                        .of(Topology.QNAME, TOPOLOGY_ID_QNAME, DEFAULT_TOPOLOGY_ID))
                        .withChild(nodeBuilder.build())
                        .build())
                .build();
    }

    private static String createNodeID(final Integer port) {
        return String.format("%d-sim-device", port);
    }

    private static LeafNode<Uint16> leafPort(final int port) {
        return Builders.<Uint16>leafBuilder()
                .withNodeIdentifier(PORT_NODE_IDENTIFIER)
                .withValue(Uint16.valueOf(port))
                .build();
    }

    private static LeafNode<String> leafHost(final String host) {
        return Builders.<String>leafBuilder()
                .withNodeIdentifier(HOST_NODE_IDENTIFIER)
                .withValue(host)
                .build();
    }

    private static ChoiceNode containerCredentials(final String username, final String password) {
        return Builders.choiceBuilder().withNodeIdentifier(CREDENTIALS_NODE_IDENTIFIER)
                .withChild(Builders.<String>leafBuilder()
                        .withNodeIdentifier(USERNAME_NODE_IDENTIFIER)
                        .withValue(username)
                        .build())
                .withChild(Builders.<String>leafBuilder()
                        .withNodeIdentifier(PASSWORD_NODE_IDENTIFIER)
                        .withValue(password)
                        .build())
                .build();
    }

    private static LeafNode<Boolean> leafTcpOnly(final Boolean tcpOnly) {
        return Builders.<Boolean>leafBuilder()
                .withNodeIdentifier(TCP_ONLY_NODE_IDENTIFIER)
                .withValue(tcpOnly)
                .build();
    }

    private static LeafNode<Integer> leafKeepaliveDelay(final Integer keepaliveDelay) {
        return Builders.<Integer>leafBuilder()
                .withNodeIdentifier(KEEPALIVE_DELAY_NODE_IDENTIFIER)
                .withValue(keepaliveDelay)
                .build();
    }

    private static LeafNode<Boolean> leafSchemaless(final Boolean schemaless) {
        return Builders.<Boolean>leafBuilder()
                .withNodeIdentifier(SCHEMALESS_NODE_IDENTIFIER)
                .withValue(schemaless)
                .build();
    }
}
