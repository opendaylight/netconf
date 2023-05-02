/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import static com.google.common.base.Verify.verify;

import java.io.IOException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.mdsal.binding.dom.codec.spi.BindingDOMCodecFactory;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeGenerator;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.common.mdsal.NormalizedDataUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.Netconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfConfigChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryUpdate;
import org.opendaylight.yangtools.yang.binding.EventInstantAware;
import org.opendaylight.yangtools.yang.binding.Notification;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.w3c.dom.Document;

final class NotificationsTransformUtil {
    private final EffectiveModelContext schemaContext;
    private final BindingNormalizedNodeSerializer serializer;

    NotificationsTransformUtil(final YangParserFactory parserFactory, final BindingRuntimeGenerator generator,
            final BindingDOMCodecFactory codecFactory) throws YangParserException {
        final var ctx = BindingRuntimeHelpers.createRuntimeContext(parserFactory, generator,
            Netconf.class, NetconfConfigChange.class, YangLibraryChange.class, YangLibraryUpdate.class);
        schemaContext = ctx.getEffectiveModelContext();
        verify(schemaContext.getOperations().stream()
                .filter(input -> input.getQName().getLocalName().equals(CreateSubscription.CREATE_SUBSCRIPTION))
                .findFirst()
                .isPresent());
        serializer = codecFactory.createBindingDOMCodec(ctx);
    }

    /**
     * Transform base notification for capabilities into NetconfNotification.
     */
    public @NonNull NotificationMessage transform(final Notification<?> notification, final Absolute path) {
        final var eventTime = notification instanceof EventInstantAware aware ? aware.eventInstant() : null;
        final var containerNode = serializer.toNormalizedNodeNotification(notification);
        final var result = new DOMResult(XmlUtil.newDocument());
        try {
            NormalizedDataUtil.writeNormalizedNode(containerNode, result, schemaContext, path);
        } catch (final XMLStreamException | IOException e) {
            throw new IllegalStateException("Unable to serialize " + notification, e);
        }

        final var node = (Document) result.getNode();
        return eventTime != null ? new NotificationMessage(node, eventTime) : new NotificationMessage(node);
    }
}
