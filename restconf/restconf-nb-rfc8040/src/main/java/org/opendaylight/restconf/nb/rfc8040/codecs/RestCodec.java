/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.codecs;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.opendaylight.restconf.common.util.IdentityValuesDTO;
import org.opendaylight.restconf.common.util.IdentityValuesDTO.IdentityValue;
import org.opendaylight.restconf.common.util.IdentityValuesDTO.Predicate;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.codec.IdentityrefCodec;
import org.opendaylight.yangtools.yang.data.api.codec.InstanceIdentifierCodec;
import org.opendaylight.yangtools.yang.data.api.codec.LeafrefCodec;
import org.opendaylight.yangtools.yang.model.api.AnyxmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: what is this class even trying to do?
public final class RestCodec {
    private static final Logger LOG = LoggerFactory.getLogger(RestCodec.class);

    private RestCodec() {
        // Hidden on purpose
    }

    public static class IdentityrefCodecImpl implements IdentityrefCodec<IdentityValuesDTO> {
        private static final Logger LOG = LoggerFactory.getLogger(IdentityrefCodecImpl.class);

        private final SchemaContext schemaContext;

        public IdentityrefCodecImpl(final SchemaContext schemaContext) {
            this.schemaContext = schemaContext;
        }

        @Override
        public IdentityValuesDTO serialize(final QName data) {
            return new IdentityValuesDTO(data.getNamespace().toString(), data.getLocalName(), null, null);
        }

        @Override
        @SuppressFBWarnings(value = "NP_NONNULL_RETURN_VIOLATION", justification = "Legacy return")
        public QName deserialize(final IdentityValuesDTO data) {
            final IdentityValue valueWithNamespace = data.getValuesWithNamespaces().get(0);
            final Module module = getModuleByNamespace(valueWithNamespace.getNamespace(), schemaContext);
            // FIXME: this needs to be a hard error
            if (module == null) {
                LOG.info("Module was not found for namespace {}", valueWithNamespace.getNamespace());
                LOG.info("Idenetityref will be translated as NULL for data - {}", String.valueOf(valueWithNamespace));
                return null;
            }

            return QName.create(module.getNamespace(), module.getRevision(), valueWithNamespace.getValue());
        }

    }

    public static class LeafrefCodecImpl implements LeafrefCodec<String> {

        @Override
        public String serialize(final Object data) {
            return String.valueOf(data);
        }

        @Override
        public Object deserialize(final String data) {
            return data;
        }

    }

    public static class InstanceIdentifierCodecImpl implements InstanceIdentifierCodec<IdentityValuesDTO> {
        private static final Logger LOG = LoggerFactory.getLogger(InstanceIdentifierCodecImpl.class);

        private final SchemaContext schemaContext;

        public InstanceIdentifierCodecImpl(final SchemaContext schemaContext) {
            this.schemaContext = schemaContext;
        }

        @Override
        public IdentityValuesDTO serialize(final YangInstanceIdentifier data) {
            final IdentityValuesDTO identityValuesDTO = new IdentityValuesDTO();
            for (final PathArgument pathArgument : data.getPathArguments()) {
                final IdentityValue identityValue = qNameToIdentityValue(pathArgument.getNodeType());
                if (pathArgument instanceof NodeIdentifierWithPredicates && identityValue != null) {
                    final List<Predicate> predicates =
                            keyValuesToPredicateList(((NodeIdentifierWithPredicates) pathArgument).entrySet());
                    identityValue.setPredicates(predicates);
                } else if (pathArgument instanceof NodeWithValue && identityValue != null) {
                    final List<Predicate> predicates = new ArrayList<>();
                    final String value = String.valueOf(((NodeWithValue<?>) pathArgument).getValue());
                    predicates.add(new Predicate(null, value));
                    identityValue.setPredicates(predicates);
                }
                identityValuesDTO.add(identityValue);
            }
            return identityValuesDTO;
        }

        @SuppressFBWarnings(value = {
            "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
            "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            "NP_NONNULL_RETURN_VIOLATION"
        }, justification = "Unrecognised NullableDecl")
        @Override
        public YangInstanceIdentifier deserialize(final IdentityValuesDTO data) {
            final List<PathArgument> result = new ArrayList<>();
            final IdentityValue valueWithNamespace = data.getValuesWithNamespaces().get(0);
            final Module module = getModuleByNamespace(valueWithNamespace.getNamespace(), schemaContext);
            // FIXME: this needs to be a hard error
            if (module == null) {
                LOG.info("Module by namespace '{}' of first node in instance-identifier was not found.",
                        valueWithNamespace.getNamespace());
                LOG.info("Instance-identifier will be translated as NULL for data - {}",
                        String.valueOf(valueWithNamespace.getValue()));
                return null;
            }

            DataNodeContainer parentContainer = module;
            final List<IdentityValue> identities = data.getValuesWithNamespaces();
            for (int i = 0; i < identities.size(); i++) {
                final IdentityValue identityValue = identities.get(i);
                XMLNamespace validNamespace = resolveValidNamespace(identityValue.getNamespace(), schemaContext);
                final DataSchemaNode node = findInstanceDataChildByNameAndNamespace(
                        parentContainer, identityValue.getValue(), validNamespace);
                // FIXME: this needs to be a hard error
                if (node == null) {
                    LOG.info("'{}' node was not found in {}", identityValue, parentContainer.getChildNodes());
                    LOG.info("Instance-identifier will be translated as NULL for data - {}",
                            String.valueOf(identityValue.getValue()));
                    return null;
                }
                final QName qName = node.getQName();
                PathArgument pathArgument = null;
                if (identityValue.getPredicates().isEmpty()) {
                    pathArgument = new NodeIdentifier(qName);
                } else if (node instanceof LeafListSchemaNode) { // predicate is value of leaf-list entry
                    final Predicate leafListPredicate = identityValue.getPredicates().get(0);
                    // FIXME: this needs to be a hard error
                    if (!leafListPredicate.isLeafList()) {
                        LOG.info("Predicate's data is not type of leaf-list. It should be in format \".='value'\"");
                        LOG.info("Instance-identifier will be translated as NULL for data - {}",
                                String.valueOf(identityValue.getValue()));
                        return null;
                    }
                    pathArgument = new NodeWithValue<>(qName, leafListPredicate.getValue());
                } else if (node instanceof ListSchemaNode) { // predicates are keys of list
                    final DataNodeContainer listNode = (DataNodeContainer) node;
                    final Map<QName, Object> predicatesMap = new HashMap<>();
                    for (final Predicate predicate : identityValue.getPredicates()) {
                        validNamespace = resolveValidNamespace(predicate.getName().getNamespace(), schemaContext);
                        final DataSchemaNode listKey = findInstanceDataChildByNameAndNamespace(listNode,
                                predicate.getName().getValue(), validNamespace);
                        predicatesMap.put(listKey.getQName(), predicate.getValue());
                    }
                    pathArgument = NodeIdentifierWithPredicates.of(qName, predicatesMap);
                } else {
                    // FIXME: this needs to be a hard error
                    LOG.info("Node {} is not List or Leaf-list.", node);
                    LOG.info("Instance-identifier will be translated as NULL for data - {}",
                            String.valueOf(identityValue.getValue()));
                    return null;
                }
                result.add(pathArgument);
                if (i < identities.size() - 1) { // last element in instance-identifier can be other than
                    // DataNodeContainer
                    if (node instanceof DataNodeContainer) {
                        parentContainer = (DataNodeContainer) node;
                    } else {
                        // FIXME: this needs to be a hard error
                        LOG.info("Node {} isn't instance of DataNodeContainer", node);
                        LOG.info("Instance-identifier will be translated as NULL for data - {}",
                                String.valueOf(identityValue.getValue()));
                        return null;
                    }
                }
            }

            return result.isEmpty() ? null : YangInstanceIdentifier.create(result);
        }

        private static List<Predicate> keyValuesToPredicateList(final Set<Entry<QName, Object>> keyValues) {
            final List<Predicate> result = new ArrayList<>();
            for (final Entry<QName, Object> entry : keyValues) {
                final QName qualifiedName = entry.getKey();
                final Object value = entry.getValue();
                result.add(new Predicate(qNameToIdentityValue(qualifiedName), String.valueOf(value)));
            }
            return result;
        }

        private static IdentityValue qNameToIdentityValue(final QName qualifiedName) {
            if (qualifiedName != null) {
                return new IdentityValue(qualifiedName.getNamespace().toString(), qualifiedName.getLocalName());
            }
            return null;
        }
    }

    private static Module getModuleByNamespace(final String namespace, final SchemaContext schemaContext) {
        final var validNamespace = resolveValidNamespace(namespace, schemaContext);
        final var it = schemaContext.findModules(validNamespace).iterator();
        if (!it.hasNext()) {
            LOG.info("Module for namespace {} was not found.", validNamespace);
            return null;
        }
        return it.next();
    }

    private static XMLNamespace resolveValidNamespace(final String namespace, final SchemaContext schemaContext) {
        XMLNamespace validNamespace = findFirstModuleByName(schemaContext, namespace);
        return validNamespace != null ? validNamespace
            // FIXME: what the heck?!
            : XMLNamespace.of(namespace);
    }

    private static XMLNamespace findFirstModuleByName(final SchemaContext schemaContext, final String name) {
        for (final Module module : schemaContext.getModules()) {
            if (module.getName().equals(name)) {
                return module.getNamespace();
            }
        }
        return null;
    }

    private static DataSchemaNode findInstanceDataChildByNameAndNamespace(final DataNodeContainer container,
            final String name, final XMLNamespace namespace) {
        requireNonNull(namespace);

        final Iterable<DataSchemaNode> result = Iterables.filter(findInstanceDataChildrenByName(container, name),
            node -> namespace.equals(node.getQName().getNamespace()));
        return Iterables.getFirst(result, null);
    }

    private static List<DataSchemaNode> findInstanceDataChildrenByName(final DataNodeContainer container,
            final String name) {
        final List<DataSchemaNode> instantiatedDataNodeContainers = new ArrayList<>();
        collectInstanceDataNodeContainers(instantiatedDataNodeContainers, requireNonNull(container),
            requireNonNull(name));
        return instantiatedDataNodeContainers;
    }

    private static void collectInstanceDataNodeContainers(final List<DataSchemaNode> potentialSchemaNodes,
            final DataNodeContainer container, final String name) {

        final Iterable<? extends DataSchemaNode> nodes =
                Iterables.filter(container.getChildNodes(), node -> name.equals(node.getQName().getLocalName()));

        // Can't combine this loop with the filter above because the filter is
        // lazily-applied by Iterables.filter.
        for (final DataSchemaNode potentialNode : nodes) {
            if (isInstantiatedDataSchema(potentialNode)) {
                potentialSchemaNodes.add(potentialNode);
            }
        }

        final Iterable<ChoiceSchemaNode> choiceNodes =
                Iterables.filter(container.getChildNodes(), ChoiceSchemaNode.class);
        final Iterable<Collection<? extends CaseSchemaNode>> map = Iterables.transform(choiceNodes,
            ChoiceSchemaNode::getCases);
        for (final CaseSchemaNode caze : Iterables.concat(map)) {
            collectInstanceDataNodeContainers(potentialSchemaNodes, caze, name);
        }
    }

    private static boolean isInstantiatedDataSchema(final DataSchemaNode node) {
        return node instanceof LeafSchemaNode || node instanceof LeafListSchemaNode
                || node instanceof ContainerSchemaNode || node instanceof ListSchemaNode
                || node instanceof AnyxmlSchemaNode;
    }
}
