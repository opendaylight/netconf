/*
 * Copyright (c) 2019 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.rfc8528.model.api.SchemaMountConstants;
import org.opendaylight.yangtools.yang.common.MountPointLabel;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContextFactory;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.spi.AbstractEffectiveModelContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: this should really come from rfc8528-data-util
final class DeviceMountPointContext extends AbstractEffectiveModelContextProvider implements Immutable,
        MountPointContext {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceMountPointContext.class);
    private static final NodeIdentifier MOUNT_POINT = NodeIdentifier.create(
        QName.create(SchemaMountConstants.RFC8528_MODULE, "mount-point").intern());
    private static final NodeIdentifier CONFIG = NodeIdentifier.create(
        QName.create(SchemaMountConstants.RFC8528_MODULE, "config").intern());
    private static final NodeIdentifier MODULE = NodeIdentifier.create(
        QName.create(SchemaMountConstants.RFC8528_MODULE, "module").intern());
    private static final NodeIdentifier LABEL = NodeIdentifier.create(
        QName.create(SchemaMountConstants.RFC8528_MODULE, "label").intern());
    private static final NodeIdentifier SCHEMA_REF = NodeIdentifier.create(
        QName.create(SchemaMountConstants.RFC8528_MODULE, "schema-ref").intern());
    private static final NodeIdentifier INLINE = NodeIdentifier.create(
        QName.create(SchemaMountConstants.RFC8528_MODULE, "inline").intern());
    private static final NodeIdentifier SHARED_SCHEMA = NodeIdentifier.create(
        QName.create(SchemaMountConstants.RFC8528_MODULE, "shared-schema").intern());
    private static final NodeIdentifier PARENT_REFERENCE = NodeIdentifier.create(
        QName.create(SchemaMountConstants.RFC8528_MODULE, "parent-reference").intern());

    private final ImmutableMap<MountPointLabel, NetconfMountPointContextFactory> mountPoints;

    private DeviceMountPointContext(final EffectiveModelContext schemaContext,
            final Map<MountPointLabel, NetconfMountPointContextFactory> mountPoints) {
        super(schemaContext);
        this.mountPoints = ImmutableMap.copyOf(mountPoints);
    }

    static MountPointContext create(final MountPointContext emptyContext, final ContainerNode mountData) {
        final Optional<DataContainerChild> optMountPoint = mountData.findChildByArg(MOUNT_POINT);
        if (optMountPoint.isEmpty()) {
            LOG.debug("mount-point list not present in {}", mountData);
            return emptyContext;
        }

        final EffectiveModelContext schemaContext = emptyContext.getEffectiveModelContext();
        final DataContainerChild mountPoint = optMountPoint.orElseThrow();
        checkArgument(mountPoint instanceof MapNode, "mount-point list %s is not a MapNode", mountPoint);

        final Map<MountPointLabel, NetconfMountPointContextFactory> mountPoints = new HashMap<>();
        for (MapEntryNode entry : ((MapNode) mountPoint).body()) {
            final String moduleName = entry.findChildByArg(MODULE).map(mod -> {
                checkArgument(mod instanceof LeafNode, "Unexpected module leaf %s", mod);
                final Object value = mod.body();
                checkArgument(value instanceof String, "Unexpected module leaf value %s", value);
                return (String) value;
            }).orElseThrow(() -> new IllegalArgumentException("Mount module missing in " + entry));
            final Iterator<? extends Module> it = schemaContext.findModules(moduleName).iterator();
            checkArgument(it.hasNext(), "Failed to find a module named %s", moduleName);
            final QNameModule module = it.next().getQNameModule();

            final MountPointLabel mountId = new MountPointLabel(QName.create(module,
                entry.findChildByArg(LABEL).map(lbl -> {
                    checkArgument(lbl instanceof LeafNode, "Unexpected label leaf %s", lbl);
                    final Object value = lbl.body();
                    checkArgument(value instanceof String, "Unexpected label leaf value %s", value);
                    return (String) value;
                }).orElseThrow(() -> new IllegalArgumentException("Mount module missing in " + entry))));

            final DataContainerChild child = entry.findChildByArg(SCHEMA_REF).orElseThrow(
                () -> new IllegalArgumentException("Missing schema-ref choice in " + entry));
            checkArgument(child instanceof ChoiceNode, "Unexpected schema-ref choice %s", child);
            final ChoiceNode schemaRef = (ChoiceNode) child;

            final Optional<DataContainerChild> maybeShared = schemaRef.findChildByArg(SHARED_SCHEMA);
            if (maybeShared.isEmpty()) {
                LOG.debug("Ignoring non-shared mountpoint entry {}", entry);
                continue;
            }

            mountPoints.put(mountId, new NetconfMountPointContextFactory(schemaContext));
        }

        return new DeviceMountPointContext(schemaContext, mountPoints);
    }

    @Override
    public Optional<MountPointContextFactory> findMountPoint(@NonNull final MountPointLabel label) {
        return Optional.ofNullable(mountPoints.get(requireNonNull(label)));
    }
}
