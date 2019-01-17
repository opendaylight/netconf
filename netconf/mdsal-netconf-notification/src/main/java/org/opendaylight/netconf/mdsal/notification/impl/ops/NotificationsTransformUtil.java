/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.notification.impl.ops;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import org.opendaylight.mdsal.binding.dom.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.$YangModuleInfoImpl;
import org.opendaylight.yangtools.yang.binding.Notification;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.w3c.dom.Document;

public final class NotificationsTransformUtil {
    static final SchemaContext NOTIFICATIONS_SCHEMA_CTX;
    static final BindingNormalizedNodeCodecRegistry CODEC_REGISTRY;
    static final RpcDefinition CREATE_SUBSCRIPTION_RPC;

    static {
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        moduleInfoBackedContext.addModuleInfos(Collections.singletonList($YangModuleInfoImpl.getInstance()));
        moduleInfoBackedContext.addModuleInfos(Collections.singletonList(org.opendaylight.yang.gen.v1.urn.ietf.params
                .xml.ns.yang.ietf.netconf.notifications.rev120206.$YangModuleInfoImpl.getInstance()));
        final Optional<SchemaContext> schemaContextOptional = moduleInfoBackedContext.tryToCreateSchemaContext();
        checkState(schemaContextOptional.isPresent());
        NOTIFICATIONS_SCHEMA_CTX = schemaContextOptional.get();

        CREATE_SUBSCRIPTION_RPC = requireNonNull(findCreateSubscriptionRpc());

        CODEC_REGISTRY = new BindingNormalizedNodeCodecRegistry(BindingRuntimeContext.create(moduleInfoBackedContext,
                NOTIFICATIONS_SCHEMA_CTX));
    }

    private NotificationsTransformUtil() {

    }

    @SuppressFBWarnings(value = "NP_NONNULL_PARAM_VIOLATION", justification = "Unrecognised NullableDecl")
    private static RpcDefinition findCreateSubscriptionRpc() {
        return NOTIFICATIONS_SCHEMA_CTX.getOperations().stream()
                .filter(input -> input.getQName().getLocalName().equals(CreateSubscription.CREATE_SUBSCRIPTION))
                .findFirst().orElse(null);
    }

    /**
     * Transform base notification for capabilities into NetconfNotification.
     */
    public static NetconfNotification transform(final Notification notification, final SchemaPath path) {
        return transform(notification, Optional.empty(), path);
    }

    public static NetconfNotification transform(final Notification notification, final Date eventTime,
            final SchemaPath path) {
        return transform(notification, Optional.ofNullable(eventTime), path);
    }

    private static NetconfNotification transform(final Notification notification, final Optional<Date> eventTime,
            final SchemaPath path) {
        final ContainerNode containerNode = CODEC_REGISTRY.toNormalizedNodeNotification(notification);
        final Document node = XmlUtil.newDocument();
        try {
            NetconfUtil.writeNormalizedNode(node, containerNode, NOTIFICATIONS_SCHEMA_CTX, path);
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to serialize " + notification, e);
        }
        return eventTime.isPresent() ? new NetconfNotification(node, eventTime.get()) : new NetconfNotification(node);
    }
}
