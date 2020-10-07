/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.notification.impl.ops;

import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;
import javax.inject.Singleton;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.mdsal.binding.dom.codec.spi.BindingDOMCodecFactory;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeGenerator;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.Netconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfConfigChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryUpdate;
import org.opendaylight.yangtools.yang.binding.Notification;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.model.parser.api.YangParserFactory;
import org.w3c.dom.Document;

@Singleton
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
    public NetconfNotification transform(final Notification notification, final SchemaPath path) {
        return transform(notification, Optional.empty(), path);
    }

    public NetconfNotification transform(final Notification notification, final Date eventTime, final SchemaPath path) {
        return transform(notification, Optional.ofNullable(eventTime), path);
    }

    private NetconfNotification transform(final Notification notification, final Optional<Date> eventTime,
            final SchemaPath path) {
        final ContainerNode containerNode = serializer.toNormalizedNodeNotification(notification);
        final DOMResult result = new DOMResult(XmlUtil.newDocument());
        try {
            NetconfUtil.writeNormalizedNode(containerNode, result, path, schemaContext);
        } catch (final XMLStreamException | IOException e) {
            throw new IllegalStateException("Unable to serialize " + notification, e);
        }
        final Document node = (Document) result.getNode();
        return eventTime.isPresent() ? new NetconfNotification(node, eventTime.get()) : new NetconfNotification(node);
    }
}
