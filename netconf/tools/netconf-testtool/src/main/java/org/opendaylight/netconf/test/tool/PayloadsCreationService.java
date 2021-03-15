/*
 * Copyright (c) 2021 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import static com.google.common.base.Preconditions.checkState;
import static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.math.IntMath;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeFields;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.AugmentationTarget;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.stmt.AugmentEffectiveStatement;

public class PayloadsCreationService {
    private final TesttoolParameters parameters;

    private static final String RESTCONF_NETCONF_TOPOLOGY_PATH_TEMPLATE =
            "http://%s:%s/rests/data/network-topology:network-topology/topology=topology-netconf";

    private static final String DEFAULT_TOPOLOGY_ID = "topology-netconf";

    private static final String NODE_ID_TEMPLATE = "%d-sim-device";
    private static final String DEFAULT_NODE_PASSWORD = "admin";
    private static final String DEFAULT_NODE_USERNAME = "admin";
    private static final int DEFAULT_NODE_KEEPALIVE_DELAY = 0;
    private static final Boolean DEFAULT_NODE_SCHEMALESS = false;

    private static final int DEFAULT_REQUEST_PAYLOAD_INDENTATION = 2;

    private static final EffectiveModelContext NETWORK_TOPOLOGY_SCHEMA_CONTEXT = BindingRuntimeHelpers
            .createEffectiveModel(ImmutableList.of($YangModuleInfoImpl.getInstance()));

    private static final JSONCodecFactory NETWORK_TOPOLOGY_JSON_CODEC_FACTORY =
            JSONCodecFactorySupplier.DRAFT_LHOTKA_NETMOD_YANG_JSON_02.getShared(NETWORK_TOPOLOGY_SCHEMA_CONTEXT);

    private static final QName NODE_ID_QNAME = QName.create(Node.QNAME, "node-id");
    private static final QName TOPOLOGY_ID_QNAME = QName.create(Topology.QNAME, "topology-id");
    private static final QName NETCONF_NODE_AUGMENTATION_QNAME = QName.create(NetconfNodeFields.QNAME, "netconf-node");

    private static final SchemaPath NETWORK_TOPOLOGY_PATH = SchemaPath.create(true, NetworkTopology.QNAME);

    private static final NodeIdentifier PORT_NODE_IDENTIFIER = netconfNodeFieldsLeafIdentifier("port");
    private static final NodeIdentifier HOST_NODE_IDENTIFIER = netconfNodeFieldsLeafIdentifier("host");
    private static final NodeIdentifier USERNAME_NODE_IDENTIFIER = netconfNodeFieldsLeafIdentifier("username");
    private static final NodeIdentifier PASSWORD_NODE_IDENTIFIER = netconfNodeFieldsLeafIdentifier("password");
    private static final NodeIdentifier CREDENTIALS_NODE_IDENTIFIER = new NodeIdentifier(Credentials.QNAME);
    private static final NodeIdentifier TCP_ONLY_NODE_IDENTIFIER = netconfNodeFieldsLeafIdentifier("tcp-only");
    private static final NodeIdentifier KEEPALIVE_DELAY_NODE_IDENTIFIER = netconfNodeFieldsLeafIdentifier(
            "keepalive-delay");
    private static final NodeIdentifier SCHEMALESS_NODE_IDENTIFIER = netconfNodeFieldsLeafIdentifier("schemaless");
    private static final NodeIdentifier NETWORK_TOPOLOGY_NODE_NODE_IDENTIFIER = new NodeIdentifier(Node.QNAME);
    private static final NodeIdentifier TOPOLOGY_NODE_IDENTIFIER = new NodeIdentifier(Topology.QNAME);

    private static final AugmentationIdentifier NETCONF_NODE_AUGMENTATION_IDENTIFIER;

    //initialize netconf node augmentation identifier
    static {
        final Optional<DataSchemaNode> nodeSchemaOptional = NETWORK_TOPOLOGY_SCHEMA_CONTEXT
                .findDataChildByName(NetworkTopology.QNAME, Topology.QNAME, Node.QNAME);

        checkState(nodeSchemaOptional.isPresent(), "%s was not found", Node.QNAME);

        final DataSchemaNode nodeSchema = nodeSchemaOptional.get();

        final Optional<AugmentEffectiveStatement> netconfNodeStatementOptional =
                ((AugmentationTarget) nodeSchema).getAvailableAugmentations().stream()
                        .filter(aug -> aug instanceof AugmentEffectiveStatement)
                        .map(augmentStatement -> augmentStatement.asEffectiveStatement())
                        .filter(augmentStatement -> augmentStatement.effectiveSubstatements().stream()
                                .anyMatch(es -> es instanceof SchemaNode
                                        && ((SchemaNode) es).getQName().equals(NETCONF_NODE_AUGMENTATION_QNAME)))
                        .findAny();

        checkState(netconfNodeStatementOptional.isPresent(), "%s augmentation of %s was not found",
                NETCONF_NODE_AUGMENTATION_QNAME, Node.QNAME);

        final AugmentEffectiveStatement netconfNodeStatement = netconfNodeStatementOptional.get();

        checkState(netconfNodeStatement instanceof DataNodeContainer, "%s augmentation of %s should be instance of %s",
                NETCONF_NODE_AUGMENTATION_QNAME, Node.QNAME, DataNodeContainer.class);

        final Set<QName> qNames = ((DataNodeContainer)netconfNodeStatement).getChildNodes().stream()
                .map(SchemaNode::getQName)
                .collect(Collectors.toUnmodifiableSet());

        NETCONF_NODE_AUGMENTATION_IDENTIFIER = new AugmentationIdentifier(qNames);
    }

    public PayloadsCreationService(TesttoolParameters parameters) {
        this.parameters = parameters;
    }

    public List<List<Execution.DestToPayload>> getThreadsPayloads(final List<Integer> openDevices) {
        final String restconfNetconfTopologyPath = String.format(RESTCONF_NETCONF_TOPOLOGY_PATH_TEMPLATE,
                parameters.controllerIp, parameters.controllerPort);
        final List<NormalizedNode<?, ?>> payloads = createPayloads(openDevices);
        final List<Execution.DestToPayload> destinationPayloadPairs = payloads.stream()
                .map(PayloadsCreationService::normalizedNodeToString)
                .map(stringPayload -> new Execution.DestToPayload(restconfNetconfTopologyPath, stringPayload))
                .collect(Collectors.toList());

        final int requestsPerThread = IntMath.divide(destinationPayloadPairs.size(), parameters.threadAmount,
                RoundingMode.UP);

        return Lists.partition(destinationPayloadPairs, requestsPerThread);
    }

    private static String normalizedNodeToString(final NormalizedNode<?, ?> node) {
        final Writer writer = new StringWriter();
        final JsonWriter jsonWriter = JsonWriterFactory.createJsonWriter(writer, DEFAULT_REQUEST_PAYLOAD_INDENTATION);
        final NormalizedNodeStreamWriter jsonStream = JSONNormalizedNodeStreamWriter.createExclusiveWriter(
                NETWORK_TOPOLOGY_JSON_CODEC_FACTORY, NETWORK_TOPOLOGY_PATH, null, jsonWriter);
        try (NormalizedNodeWriter nodeWriter = NormalizedNodeWriter.forStreamWriter(jsonStream)) {
            nodeWriter.write(node);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to serialize" + node + "to json", e);
        }
        return writer.toString();
    }

    private List<NormalizedNode<?, ?>> createPayloads(final List<Integer> openDevices) {
        final List<NormalizedNode<?, ?>> payloads;
        if (parameters.generateConfigBatchSize > 1) {
            final List<List<Integer>> portsInBatches = Lists.partition(openDevices, parameters.generateConfigBatchSize);
            payloads = createBatchedPayloads(portsInBatches);
        } else {
            payloads = createSingleNodePayloads(openDevices);
        }
        return payloads;
    }

    private List<NormalizedNode<?, ?>> createBatchedPayloads(final List<List<Integer>> portsInBatches) {
        final List<List<String>> nodeIdsInBatches = portsInBatches.stream()
                .map(portsInRequest -> portsInRequest.stream()
                        .map(port -> String.format(NODE_ID_TEMPLATE, port))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
        return createBatchedPayloads(DEFAULT_TOPOLOGY_ID, portsInBatches, nodeIdsInBatches,
                parameters.generateConfigsAddress, DEFAULT_NODE_USERNAME, DEFAULT_NODE_PASSWORD, !parameters.ssh,
                DEFAULT_NODE_KEEPALIVE_DELAY, DEFAULT_NODE_SCHEMALESS);
    }

    private List<NormalizedNode<?, ?>> createBatchedPayloads(final String topologyId,
                                                             final List<List<Integer>> portsInBatches,
                                                             final List<List<String>> nodeIdsInBatches,
                                                             final String host, final String username,
                                                             final String password, final boolean tcpOnly,
                                                             final int keepaliveDelay, final boolean schemaless) {
        final List<NormalizedNode<?, ?>> payloads = new ArrayList<>();
        final Iterator<List<String>> nodeIdsIterator = nodeIdsInBatches.iterator();
        for (final List<Integer> portsInBatch : portsInBatches) {
            final List<String> nodeIdsInBatch = nodeIdsIterator.next();
            payloads.add(createMultipleNodeTopology(topologyId, portsInBatch, nodeIdsInBatch ,host, username, password,
                    tcpOnly, keepaliveDelay, schemaless));
        }
        return payloads;
    }

    private List<NormalizedNode<?, ?>> createSingleNodePayloads(final List<Integer> batchedPorts) {
        final List<String> nodeIdsInBatches = batchedPorts.stream()
                .map(port -> String.format(NODE_ID_TEMPLATE, port))
                .collect(Collectors.toList());
        return createSingleNodePayloads(DEFAULT_TOPOLOGY_ID, batchedPorts, nodeIdsInBatches,
                parameters.generateConfigsAddress, DEFAULT_NODE_USERNAME, DEFAULT_NODE_PASSWORD, !parameters.ssh,
                DEFAULT_NODE_KEEPALIVE_DELAY, DEFAULT_NODE_SCHEMALESS);
    }

    private List<NormalizedNode<?, ?>> createSingleNodePayloads(final String topologyId, final List<Integer> ports,
                                                                final List<String> nodeIds, final String host,
                                                                final String username, final String password,
                                                                final boolean tcpOnly, final int keepaliveDelay,
                                                                final boolean schemaless) {
        final List<NormalizedNode<?, ?>> payloads = new ArrayList<>();
        final Iterator<String> nodeIdsIterator = nodeIds.iterator();
        for (final int port : ports) {
            final String nodeId = nodeIdsIterator.next();
            payloads.add(createSingleNodeTopology(topologyId, port, nodeId, host, username, password, tcpOnly,
                    keepaliveDelay, schemaless));
        }
        return payloads;
    }

    private NormalizedNode<?, ?> createMultipleNodeTopology(final String topologyId, final Iterable<Integer> ports,
                                                            final Iterable<String> nodeIds, final String host,
                                                            final String username, final String password,
                                                            final boolean tcpOnly, final int keepaliveDelay,
                                                            final boolean schemaless) {
        final CollectionNodeBuilder<MapEntryNode, MapNode> nodeListBuilder = Builders.mapBuilder()
                .withNodeIdentifier(NETWORK_TOPOLOGY_NODE_NODE_IDENTIFIER);

        final Iterator<String> nodeIdsIterator = nodeIds.iterator();
        for (final Integer port : ports) {
            final AugmentationNode netconfNode = createNetconfNode(port, host, username, password, tcpOnly,
                    keepaliveDelay, schemaless);

            final String nodeId = nodeIdsIterator.next();
            nodeListBuilder.addChild(Builders.mapEntryBuilder()
                    .withNodeIdentifier(nodeEntryIdentifier(nodeId))
                    .withChild(netconfNode)
                    .build());
        }

        final DataContainerChild<?, ?> node = nodeListBuilder.build();

        return createTopology(topologyId, node);
    }

    private NormalizedNode<?, ?> createSingleNodeTopology(final String topologyId, final int port, final String nodeId,
                                                          final String host, final String username,
                                                          final String password, final boolean tcpOnly,
                                                          final int keepaliveDelay, final boolean schemaless) {
        final AugmentationNode netconfNode = createNetconfNode(port, host, username, password, tcpOnly, keepaliveDelay,
                schemaless);

        final DataContainerChild<?, ?> node = Builders.mapBuilder()
                .withNodeIdentifier(NETWORK_TOPOLOGY_NODE_NODE_IDENTIFIER)
                .addChild(Builders.mapEntryBuilder().withNodeIdentifier(nodeEntryIdentifier(nodeId))
                        .withChild(netconfNode)
                        .build())
                .build();

        return createTopology(topologyId, node);
    }

    private MapNode createTopology(final String topologyId, final DataContainerChild<?, ?> node) {
        return Builders.mapBuilder()
                .withNodeIdentifier(TOPOLOGY_NODE_IDENTIFIER)
                .addChild(Builders.mapEntryBuilder().withNodeIdentifier(
                        NodeIdentifierWithPredicates.of(Node.QNAME, ImmutableMap.of(
                                TOPOLOGY_ID_QNAME, topologyId)))
                        .withChild(node)
                        .build())
                .build();
    }

    private AugmentationNode createNetconfNode(final int port, final String host, final String username,
                                               final String password, final Boolean tcpOnly, final int keepaliveDelay,
                                               final boolean schemaless) {
        return Builders.augmentationBuilder().withNodeIdentifier(NETCONF_NODE_AUGMENTATION_IDENTIFIER)
                .withChild(leafPort(port))
                .withChild(leafHost(host))
                .withChild(containerCredentials(username, password))
                .withChild(leafTcpOnly(tcpOnly))
                .withChild(leafKeepaliveDelay(keepaliveDelay))
                .withChild(leafSchemaless(schemaless))
                .build();
    }

    private NodeIdentifierWithPredicates nodeEntryIdentifier(final String nodeId) {
        return NodeIdentifierWithPredicates.of(Node.QNAME, ImmutableMap.of(NODE_ID_QNAME, nodeId));
    }

    private LeafNode<Uint16> leafPort(final int port) {
        return Builders.<Uint16>leafBuilder()
                .withNodeIdentifier(PORT_NODE_IDENTIFIER)
                .withValue(Uint16.valueOf(port))
                .build();
    }

    private LeafNode<String> leafHost(final String host) {
        return Builders.<String>leafBuilder()
                .withNodeIdentifier(HOST_NODE_IDENTIFIER)
                .withValue(host)
                .build();
    }

    private ChoiceNode containerCredentials(final String username, final String password) {
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

    private LeafNode<Boolean> leafTcpOnly(final Boolean tcpOnly) {
        return Builders.<Boolean>leafBuilder()
                .withNodeIdentifier(TCP_ONLY_NODE_IDENTIFIER)
                .withValue(tcpOnly)
                .build();
    }

    private LeafNode<Integer> leafKeepaliveDelay(final Integer keepaliveDelay) {
        return Builders.<Integer>leafBuilder()
                .withNodeIdentifier(KEEPALIVE_DELAY_NODE_IDENTIFIER)
                .withValue(keepaliveDelay)
                .build();
    }

    private LeafNode<Boolean> leafSchemaless(final Boolean schemaless) {
        return Builders.<Boolean>leafBuilder()
                .withNodeIdentifier(SCHEMALESS_NODE_IDENTIFIER)
                .withValue(schemaless)
                .build();
    }

    private static NodeIdentifier netconfNodeFieldsLeafIdentifier(final String localName) {
        return new NodeIdentifier(QName.create(NetconfNodeFields.QNAME, localName));
    }
}
