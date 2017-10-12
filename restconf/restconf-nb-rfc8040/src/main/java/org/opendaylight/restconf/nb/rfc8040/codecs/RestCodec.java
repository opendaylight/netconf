/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.codecs;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.restconf.common.util.IdentityValuesDTO;
import org.opendaylight.restconf.common.util.IdentityValuesDTO.IdentityValue;
import org.opendaylight.restconf.common.util.IdentityValuesDTO.Predicate;
import org.opendaylight.restconf.common.util.RestUtil;
import org.opendaylight.yangtools.concepts.Codec;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.codec.IdentityrefCodec;
import org.opendaylight.yangtools.yang.data.api.codec.InstanceIdentifierCodec;
import org.opendaylight.yangtools.yang.data.api.codec.LeafrefCodec;
import org.opendaylight.yangtools.yang.data.impl.codec.TypeDefinitionAwareCodec;
import org.opendaylight.yangtools.yang.model.api.AnyXmlSchemaNode;
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
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.InstanceIdentifierTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RestCodec {

    private static final Logger LOG = LoggerFactory.getLogger(RestCodec.class);

    private RestCodec() {
    }

    public static Codec<Object, Object> from(final TypeDefinition<?> typeDefinition,
            final DOMMountPoint mountPoint, final SchemaContext schemaContext) {
        return new ObjectCodec(typeDefinition, mountPoint, schemaContext);
    }

    @SuppressWarnings("rawtypes")
    public static final class ObjectCodec implements Codec<Object, Object> {

        private static final Logger LOG = LoggerFactory.getLogger(ObjectCodec.class);

        public static final Codec LEAFREF_DEFAULT_CODEC = new LeafrefCodecImpl();
        private final Codec instanceIdentifier;
        private final Codec identityrefCodec;

        private final TypeDefinition<?> type;

        private final SchemaContext schemaContext;

        private ObjectCodec(final TypeDefinition<?> typeDefinition, final DOMMountPoint mountPoint,
                final SchemaContext schemaContext) {
            this.schemaContext = schemaContext;
            this.type = RestUtil.resolveBaseTypeFrom(typeDefinition);
            if (this.type instanceof IdentityrefTypeDefinition) {
                this.identityrefCodec = new IdentityrefCodecImpl(mountPoint, schemaContext);
            } else {
                this.identityrefCodec = null;
            }
            if (this.type instanceof InstanceIdentifierTypeDefinition) {
                this.instanceIdentifier = new InstanceIdentifierCodecImpl(mountPoint, schemaContext);
            } else {
                this.instanceIdentifier = null;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object deserialize(final Object input) {
            try {
                if (this.type instanceof IdentityrefTypeDefinition) {
                    if (input instanceof IdentityValuesDTO) {
                        return this.identityrefCodec.deserialize(input);
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(
                            "Value is not instance of IdentityrefTypeDefinition but is {}. "
                                    + "Therefore NULL is used as translation of  - {}",
                            input == null ? "null" : input.getClass(), String.valueOf(input));
                    }
                    return null;
                } else if (this.type instanceof InstanceIdentifierTypeDefinition) {
                    if (input instanceof IdentityValuesDTO) {
                        return this.instanceIdentifier.deserialize(input);
                    } else {
                        final StringModuleInstanceIdentifierCodec codec =
                                new StringModuleInstanceIdentifierCodec(schemaContext);
                        return codec.deserialize((String) input);
                    }
                } else {
                    final TypeDefinitionAwareCodec<Object, ? extends TypeDefinition<?>> typeAwarecodec =
                            TypeDefinitionAwareCodec.from(this.type);
                    if (typeAwarecodec != null) {
                        if (input instanceof IdentityValuesDTO) {
                            return typeAwarecodec.deserialize(((IdentityValuesDTO) input).getOriginValue());
                        }
                        return typeAwarecodec.deserialize(String.valueOf(input));
                    } else {
                        LOG.debug("Codec for type \"" + this.type.getQName().getLocalName()
                                + "\" is not implemented yet.");
                        return null;
                    }
                }
            } catch (final ClassCastException e) { // TODO remove this catch when everyone use codecs
                LOG.error(
                        "ClassCastException was thrown when codec is invoked with parameter " + String.valueOf(input),
                        e);
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object serialize(final Object input) {
            try {
                if (this.type instanceof IdentityrefTypeDefinition) {
                    return this.identityrefCodec.serialize(input);
                } else if (this.type instanceof LeafrefTypeDefinition) {
                    return LEAFREF_DEFAULT_CODEC.serialize(input);
                } else if (this.type instanceof InstanceIdentifierTypeDefinition) {
                    return this.instanceIdentifier.serialize(input);
                } else {
                    final TypeDefinitionAwareCodec<Object, ? extends TypeDefinition<?>> typeAwarecodec =
                            TypeDefinitionAwareCodec.from(this.type);
                    if (typeAwarecodec != null) {
                        return typeAwarecodec.serialize(input);
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Codec for type \"" + this.type.getQName().getLocalName()
                                + "\" is not implemented yet.");
                        }
                        return null;
                    }
                }
            } catch (final ClassCastException e) { // TODO remove this catch when everyone use codecs
                LOG.error(
                        "ClassCastException was thrown when codec is invoked with parameter " + String.valueOf(input),
                        e);
                return input;
            }
        }

    }

    public static class IdentityrefCodecImpl implements IdentityrefCodec<IdentityValuesDTO> {

        private static final Logger LOG = LoggerFactory.getLogger(IdentityrefCodecImpl.class);

        private final DOMMountPoint mountPoint;

        private final SchemaContext schemaContext;

        public IdentityrefCodecImpl(final DOMMountPoint mountPoint, final SchemaContext schemaContext) {
            this.mountPoint = mountPoint;
            this.schemaContext = schemaContext;
        }

        @Override
        public IdentityValuesDTO serialize(final QName data) {
            return new IdentityValuesDTO(data.getNamespace().toString(), data.getLocalName(), null, null);
        }

        @Override
        public QName deserialize(final IdentityValuesDTO data) {
            final IdentityValue valueWithNamespace = data.getValuesWithNamespaces().get(0);
            final Module module =
                    getModuleByNamespace(valueWithNamespace.getNamespace(), this.mountPoint, schemaContext);
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
        private final DOMMountPoint mountPoint;
        private final SchemaContext schemaContext;

        public InstanceIdentifierCodecImpl(final DOMMountPoint mountPoint, final SchemaContext schemaContext) {
            this.mountPoint = mountPoint;
            this.schemaContext = schemaContext;
        }

        @Override
        public IdentityValuesDTO serialize(final YangInstanceIdentifier data) {
            final IdentityValuesDTO identityValuesDTO = new IdentityValuesDTO();
            for (final PathArgument pathArgument : data.getPathArguments()) {
                final IdentityValue identityValue = qNameToIdentityValue(pathArgument.getNodeType());
                if (pathArgument instanceof NodeIdentifierWithPredicates && identityValue != null) {
                    final List<Predicate> predicates =
                            keyValuesToPredicateList(((NodeIdentifierWithPredicates) pathArgument).getKeyValues());
                    identityValue.setPredicates(predicates);
                } else if (pathArgument instanceof NodeWithValue && identityValue != null) {
                    final List<Predicate> predicates = new ArrayList<>();
                    final String value = String.valueOf(((NodeWithValue) pathArgument).getValue());
                    predicates.add(new Predicate(null, value));
                    identityValue.setPredicates(predicates);
                }
                identityValuesDTO.add(identityValue);
            }
            return identityValuesDTO;
        }

        @Override
        public YangInstanceIdentifier deserialize(final IdentityValuesDTO data) {
            final List<PathArgument> result = new ArrayList<>();
            final IdentityValue valueWithNamespace = data.getValuesWithNamespaces().get(0);
            final Module module =
                    getModuleByNamespace(valueWithNamespace.getNamespace(), this.mountPoint, schemaContext);
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
                URI validNamespace =
                        resolveValidNamespace(identityValue.getNamespace(), this.mountPoint, schemaContext);
                final DataSchemaNode node = findInstanceDataChildByNameAndNamespace(
                        parentContainer, identityValue.getValue(), validNamespace);
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
                } else {
                    if (node instanceof LeafListSchemaNode) { // predicate is value of leaf-list entry
                        final Predicate leafListPredicate = identityValue.getPredicates().get(0);
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
                            validNamespace = resolveValidNamespace(predicate.getName().getNamespace(), this.mountPoint,
                                    schemaContext);
                            final DataSchemaNode listKey = findInstanceDataChildByNameAndNamespace(listNode,
                                    predicate.getName().getValue(), validNamespace);
                            predicatesMap.put(listKey.getQName(), predicate.getValue());
                        }
                        pathArgument = new NodeIdentifierWithPredicates(qName, predicatesMap);
                    } else {
                        LOG.info("Node {} is not List or Leaf-list.", node);
                        LOG.info("Instance-identifier will be translated as NULL for data - {}",
                                String.valueOf(identityValue.getValue()));
                        return null;
                    }
                }
                result.add(pathArgument);
                if (i < identities.size() - 1) { // last element in instance-identifier can be other than
                    // DataNodeContainer
                    if (node instanceof DataNodeContainer) {
                        parentContainer = (DataNodeContainer) node;
                    } else {
                        LOG.info("Node {} isn't instance of DataNodeContainer", node);
                        LOG.info("Instance-identifier will be translated as NULL for data - {}",
                                String.valueOf(identityValue.getValue()));
                        return null;
                    }
                }
            }

            return result.isEmpty() ? null : YangInstanceIdentifier.create(result);
        }

        private static List<Predicate> keyValuesToPredicateList(final Map<QName, Object> keyValues) {
            final List<Predicate> result = new ArrayList<>();
            for (final QName qualifiedName : keyValues.keySet()) {
                final Object value = keyValues.get(qualifiedName);
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

    private static Module getModuleByNamespace(final String namespace, final DOMMountPoint mountPoint,
            final SchemaContext schemaContext) {
        final URI validNamespace = resolveValidNamespace(namespace, mountPoint, schemaContext);
        Module module = null;
        if (mountPoint != null) {
            module = mountPoint.getSchemaContext().findModules(validNamespace).iterator().next();
        } else {
            module = schemaContext.findModules(validNamespace).iterator().next();
        }
        if (module == null) {
            LOG.info("Module for namespace " + validNamespace + " wasn't found.");
            return null;
        }
        return module;
    }

    private static URI resolveValidNamespace(final String namespace, final DOMMountPoint mountPoint,
            final SchemaContext schemaContext) {
        URI validNamespace;
        if (mountPoint != null) {
            validNamespace = findFirstModuleByName(schemaContext, namespace);
        } else {
            validNamespace = findFirstModuleByName(schemaContext, namespace);
        }
        if (validNamespace == null) {
            validNamespace = URI.create(namespace);
        }

        return validNamespace;
    }

    private static URI findFirstModuleByName(final SchemaContext schemaContext, final String name) {
        for (final Module module : schemaContext.getModules()) {
            if (module.getName().equals(name)) {
                return module.getNamespace();
            }
        }
        return null;
    }

    private static DataSchemaNode findInstanceDataChildByNameAndNamespace(final DataNodeContainer container,
            final String name, final URI namespace) {
        Preconditions.checkNotNull(namespace);

        final Iterable<DataSchemaNode> result = Iterables.filter(findInstanceDataChildrenByName(container, name),
            node -> namespace.equals(node.getQName().getNamespace()));
        return Iterables.getFirst(result, null);
    }

    private static List<DataSchemaNode> findInstanceDataChildrenByName(final DataNodeContainer container,
            final String name) {
        Preconditions.checkNotNull(container);
        Preconditions.checkNotNull(name);

        final List<DataSchemaNode> instantiatedDataNodeContainers = new ArrayList<>();
        collectInstanceDataNodeContainers(instantiatedDataNodeContainers, container, name);
        return instantiatedDataNodeContainers;
    }

    private static void collectInstanceDataNodeContainers(final List<DataSchemaNode> potentialSchemaNodes,
            final DataNodeContainer container, final String name) {

        final Iterable<DataSchemaNode> nodes =
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
        final Iterable<Collection<CaseSchemaNode>> map = Iterables.transform(choiceNodes,
            choice -> choice.getCases().values());
        for (final CaseSchemaNode caze : Iterables.concat(map)) {
            collectInstanceDataNodeContainers(potentialSchemaNodes, caze, name);
        }
    }

    private static boolean isInstantiatedDataSchema(final DataSchemaNode node) {
        return node instanceof LeafSchemaNode || node instanceof LeafListSchemaNode
                || node instanceof ContainerSchemaNode || node instanceof ListSchemaNode
                || node instanceof AnyXmlSchemaNode;
    }
}
