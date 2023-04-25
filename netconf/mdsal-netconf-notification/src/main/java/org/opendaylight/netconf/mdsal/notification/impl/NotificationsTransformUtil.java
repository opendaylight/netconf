/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.notification.impl;

import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.util.Date;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.mdsal.binding.dom.codec.spi.BindingDOMCodecFactory;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeGenerator;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.Netconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfConfigChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryUpdate;
import org.opendaylight.yangtools.yang.binding.Notification;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.w3c.dom.Document;

public final class NotificationsTransformUtil {
    private final EffectiveModelContext schemaContext;
    private final BindingNormalizedNodeSerializer serializer;

    public NotificationsTransformUtil(final YangParserFactory parserFactory, final BindingRuntimeGenerator generator,
            final BindingDOMCodecFactory codecFactory) throws YangParserException {
        final BindingRuntimeContext ctx = BindingRuntimeHelpers.createRuntimeContext(parserFactory, generator,
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
    public @NonNull NotificationMessage transform(final @NonNull Notification<?> notification, final Absolute path) {
        return transform(notification, path, null);
    }

    // FIXME: this is not exactly correct: we should be looking for EventInstantAware and fill the event time reported
    //        there. Alternatively, this information should be backfilled from whoever is sourcing the notification,
    //        if at all possible
    public @NonNull NotificationMessage transform(final @NonNull Notification<?> notification, final Absolute path,
            final @Nullable Date eventTime) {
        final var containerNode = serializer.toNormalizedNodeNotification(notification);
        final var result = new DOMResult(XmlUtil.newDocument());
        try {
            NetconfUtil.writeNormalizedNode(containerNode, result, schemaContext, path);
        } catch (final XMLStreamException | IOException e) {
            throw new IllegalStateException("Unable to serialize " + notification, e);
        }
        final Document node = (Document) result.getNode();
        return eventTime != null ? new NotificationMessage(node, eventTime) : new NotificationMessage(node);
    }
}
