/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module.ConformanceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.Deviation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.Submodule;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.tree.api.ConflictingModificationAppliedException;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A component which maintains the state of {@code ietf-yang-library} inside the datastore.
 */
// FIXME: this should be reconciled with the two other implementations we have.
@Singleton
@Component(service = { })
public final class SchemaContextHandler implements EffectiveModelContextListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaContextHandler.class);

    private static final NodeIdentifier MODULE_CONFORMANCE_NODEID =
        NodeIdentifier.create(QName.create(YangLibrary.QNAME, "conformance-type").intern());
    private static final NodeIdentifier MODULE_FEATURE_NODEID =
        NodeIdentifier.create(QName.create(YangLibrary.QNAME, "feature").intern());
    private static final NodeIdentifier MODULE_NAME_NODEID =
        NodeIdentifier.create(QName.create(YangLibrary.QNAME, "name").intern());
    private static final NodeIdentifier MODULE_NAMESPACE_NODEID =
        NodeIdentifier.create(QName.create(YangLibrary.QNAME, "namespace").intern());
    private static final NodeIdentifier MODULE_REVISION_NODEID =
        NodeIdentifier.create(QName.create(YangLibrary.QNAME, "revision").intern());
    private static final NodeIdentifier MODULE_SCHEMA_NODEID =
        NodeIdentifier.create(QName.create(YangLibrary.QNAME, "schema").intern());
    private static final NodeIdentifier MODULE_SET_ID_LEAF_NODEID =
        NodeIdentifier.create(QName.create(YangLibrary.QNAME, "module-set-id").intern());

    private final AtomicInteger moduleSetId = new AtomicInteger();
    private final DOMDataBroker domDataBroker;
    private final Registration listenerRegistration;

    private volatile EffectiveModelContext schemaContext;

    @Inject
    @Activate
    public SchemaContextHandler(@Reference final DOMDataBroker domDataBroker,
            @Reference final DOMSchemaService domSchemaService) {
        this.domDataBroker = requireNonNull(domDataBroker);
        listenerRegistration = domSchemaService.registerSchemaContextListener(this);
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        listenerRegistration.close();
    }

    @Override
    public void onModelContextUpdated(final EffectiveModelContext context) {
        schemaContext = requireNonNull(context);

        if (context.findModuleStatement(YangLibrary.QNAME.getModule()).isPresent()) {
            putData(mapModulesByIetfYangLibraryYang(context, String.valueOf(moduleSetId.incrementAndGet())));
        }
    }

    @VisibleForTesting
    EffectiveModelContext get() {
        return schemaContext;
    }

    private void putData(final ContainerNode normNode) {
        final DOMDataTreeWriteTransaction wTx = domDataBroker.newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL,
                YangInstanceIdentifier.of(NodeIdentifier.create(normNode.name().getNodeType())), normNode);
        try {
            wTx.commit().get();
        } catch (InterruptedException e) {
            throw new RestconfDocumentedException("Problem occurred while putting data to DS.", e);
        } catch (ExecutionException e) {
            final TransactionCommitFailedException failure = Throwables.getCauseAs(e,
                TransactionCommitFailedException.class);
            if (failure.getCause() instanceof ConflictingModificationAppliedException) {
                /*
                 * Ignore error when another cluster node is already putting the same data to DS.
                 * We expect that cluster is homogeneous and that node was going to write the same data
                 * (that means no retry is needed). Transaction chain reset must be invoked to be able
                 * to continue writing data with another transaction after failed transaction.
                 * This is workaround for bug https://bugs.opendaylight.org/show_bug.cgi?id=7728
                 */
                LOG.warn("Ignoring that another cluster node is already putting the same data to DS.", e);
            } else {
                throw new RestconfDocumentedException("Problem occurred while putting data to DS.", failure);
            }
        }
    }

    /**
     * Map data from modules to {@link NormalizedNode}.
     *
     * @param context schema context
     * @param moduleSetId module-set-id of actual set
     * @return mapped data as {@link NormalizedNode}
     */
    @VisibleForTesting
    public static ContainerNode mapModulesByIetfYangLibraryYang(final EffectiveModelContext context,
            final String moduleSetId) {
        final var mapBuilder = Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(Module.QNAME));
        for (var module : context.getModules()) {
            fillMapByModules(mapBuilder, Module.QNAME, false, module, context);
        }
        return Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(ModulesState.QNAME))
            .withChild(ImmutableNodes.leafNode(MODULE_SET_ID_LEAF_NODEID, moduleSetId))
            .withChild(mapBuilder.build())
            .build();
    }

    /**
     * Map data by the specific module or submodule.
     *
     * @param mapBuilder ordered list builder for children
     * @param mapQName QName corresponding to the list builder
     * @param isSubmodule true if module is specified as submodule, false otherwise
     * @param module specific module or submodule
     * @param context schema context
     */
    private static void fillMapByModules(final CollectionNodeBuilder<MapEntryNode, SystemMapNode> mapBuilder,
            final QName mapQName, final boolean isSubmodule, final ModuleLike module,
            final EffectiveModelContext context) {
        final var mapEntryBuilder = newCommonLeafsMapEntryBuilder(mapQName, module);

        mapEntryBuilder.withChild(ImmutableNodes.leafNode(MODULE_SCHEMA_NODEID,
            "/modules/" + module.getName() + "/"
            // FIXME: orElse(null) does not seem appropriate here
            + module.getQNameModule().getRevision().map(Revision::toString).orElse(null)));

        if (!isSubmodule) {
            mapEntryBuilder.withChild(ImmutableNodes.leafNode(MODULE_NAMESPACE_NODEID,
                module.getNamespace().toString()));

            // features - not mandatory
            if (module.getFeatures() != null && !module.getFeatures().isEmpty()) {
                addFeatureLeafList(mapEntryBuilder, module.getFeatures());
            }
            // deviations - not mandatory
            final ConformanceType conformance;
            if (module.getDeviations() != null && !module.getDeviations().isEmpty()) {
                addDeviationList(module, mapEntryBuilder, context);
                conformance = ConformanceType.Implement;
            } else {
                conformance = ConformanceType.Import;
            }
            mapEntryBuilder.withChild(
                ImmutableNodes.leafNode(MODULE_CONFORMANCE_NODEID, conformance.getName()));

            // submodules - not mandatory
            if (module.getSubmodules() != null && !module.getSubmodules().isEmpty()) {
                addSubmodules(module, mapEntryBuilder, context);
            }
        }
        mapBuilder.withChild(mapEntryBuilder.build());
    }

    /**
     * Mapping submodules of specific module.
     *
     * @param module module with submodules
     * @param mapEntryBuilder mapEntryBuilder of parent for mapping children
     * @param context schema context
     */
    private static void addSubmodules(final ModuleLike module,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final EffectiveModelContext context) {
        final var mapBuilder = Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(Submodule.QNAME));

        for (var submodule : module.getSubmodules()) {
            fillMapByModules(mapBuilder, Submodule.QNAME, true, submodule, context);
        }
        mapEntryBuilder.withChild(mapBuilder.build());
    }

    /**
     * Mapping deviations of specific module.
     *
     * @param module
     *             module with deviations
     * @param mapEntryBuilder
     *             mapEntryBuilder of parent for mapping children
     * @param context
     *             schema context
     */
    private static void addDeviationList(final ModuleLike module,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final EffectiveModelContext context) {
        final var deviations = Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(Deviation.QNAME));
        for (var deviation : module.getDeviations()) {
            final List<QName> ids = deviation.getTargetPath().getNodeIdentifiers();
            final QName lastComponent = ids.get(ids.size() - 1);

            deviations.withChild(newCommonLeafsMapEntryBuilder(Deviation.QNAME,
                context.findModule(lastComponent.getModule()).orElseThrow())
                .build());
        }
        mapEntryBuilder.withChild(deviations.build());
    }

    /**
     * Mapping features of specific module.
     *
     * @param mapEntryBuilder mapEntryBuilder of parent for mapping children
     * @param features features of specific module
     */
    private static void addFeatureLeafList(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Collection<? extends FeatureDefinition> features) {
        final var leafSetBuilder = Builders.<String>leafSetBuilder()
                .withNodeIdentifier(MODULE_FEATURE_NODEID);
        for (var feature : features) {
            leafSetBuilder.withChildValue(feature.getQName().getLocalName());
        }
        mapEntryBuilder.withChild(leafSetBuilder.build());
    }

    private static DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> newCommonLeafsMapEntryBuilder(
            final QName qname, final ModuleLike module) {
        final var name = module.getName();
        final var revision = module.getQNameModule().getRevision().map(Revision::toString).orElse("");
        return Builders.mapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(qname,
                Map.of(MODULE_NAME_NODEID.getNodeType(), name, MODULE_REVISION_NODEID.getNodeType(), revision)))
            .withChild(ImmutableNodes.leafNode(MODULE_NAME_NODEID, name))
            .withChild(ImmutableNodes.leafNode(MODULE_REVISION_NODEID, revision));
    }
}
