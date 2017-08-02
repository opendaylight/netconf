/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.notifications.impl.ops;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import javassist.ClassPool;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.generator.util.JavassistUtils;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.$YangModuleInfoImpl;
import org.opendaylight.yangtools.binding.data.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.yangtools.yang.binding.Notification;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public final class NotificationsTransformUtil {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationsTransformUtil.class);

    private NotificationsTransformUtil() {}

    static final SchemaContext NOTIFICATIONS_SCHEMA_CTX;
    static final BindingNormalizedNodeCodecRegistry CODEC_REGISTRY;
    static final RpcDefinition CREATE_SUBSCRIPTION_RPC;

    static {

        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        moduleInfoBackedContext.addModuleInfos(Collections.singletonList($YangModuleInfoImpl.getInstance()));
        moduleInfoBackedContext.addModuleInfos(Collections.singletonList(org.opendaylight.yang.gen.v1.urn.ietf.params
                .xml.ns.yang.ietf.netconf.notifications.rev120206.$YangModuleInfoImpl.getInstance()));
        final Optional<SchemaContext> schemaContextOptional = moduleInfoBackedContext.tryToCreateSchemaContext();
        Preconditions.checkState(schemaContextOptional.isPresent());
        NOTIFICATIONS_SCHEMA_CTX = schemaContextOptional.get();

        CREATE_SUBSCRIPTION_RPC = Preconditions.checkNotNull(findCreateSubscriptionRpc());

        Preconditions.checkNotNull(CREATE_SUBSCRIPTION_RPC);

        final JavassistUtils javassist = JavassistUtils.forClassPool(ClassPool.getDefault());
        CODEC_REGISTRY = new BindingNormalizedNodeCodecRegistry(StreamWriterGenerator.create(javassist));
        CODEC_REGISTRY.onBindingRuntimeContextUpdated(BindingRuntimeContext.create(moduleInfoBackedContext,
                NOTIFICATIONS_SCHEMA_CTX));
    }

    private static RpcDefinition findCreateSubscriptionRpc() {
        return Iterables.getFirst(Collections2.filter(NOTIFICATIONS_SCHEMA_CTX.getOperations(),
            new Predicate<RpcDefinition>() {
                @Override
                public boolean apply(final RpcDefinition input) {
                    return input.getQName().getLocalName().equals(CreateSubscription.CREATE_SUBSCRIPTION);
                }
            }), null);
    }

    /**
     * Transform base notification for capabilities into NetconfNotification.
     */
    public static NetconfNotification transform(final Notification notification, SchemaPath path) {
        return transform(notification, Optional.absent(), path);
    }

    public static NetconfNotification transform(final Notification notification,
                                                final Date eventTime, SchemaPath path) {
        return transform(notification, Optional.fromNullable(eventTime), path);
    }

    private static NetconfNotification transform(final Notification notification,
                                                 final Optional<Date> eventTime, SchemaPath path) {
        final ContainerNode containerNode = CODEC_REGISTRY.toNormalizedNodeNotification(notification);
        final DOMResult result = new DOMResult(XmlUtil.newDocument());
        try {
            NetconfUtil.writeNormalizedNode(containerNode, result, path, NOTIFICATIONS_SCHEMA_CTX);
        } catch (final XMLStreamException | IOException e) {
            throw new IllegalStateException("Unable to serialize " + notification, e);
        }
        final Document node = (Document) result.getNode();
        return eventTime.isPresent() ? new NetconfNotification(node, eventTime.get()) : new NetconfNotification(node);
    }

}
