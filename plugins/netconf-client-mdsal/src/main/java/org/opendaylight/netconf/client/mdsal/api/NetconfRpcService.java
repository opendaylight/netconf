/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.CancelCommit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.CloseSession;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Commit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.CopyConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.DeleteConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.DiscardChanges;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.KillSession;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Lock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Unlock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Validate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.GetSchema;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * A {@link DOMService} capturing the ability to invoke NETCONF RPCs through
 * {@link #invokeNetconf(QName, ContainerNode)}.
 */
public interface NetconfRpcService extends DOMService<NetconfRpcService, NetconfRpcService.Extension> {
    /**
     * Invoke a well-known RPC. This method is guaranteed to support the following RPCs:
     * <ul>
     *   <li>{@link CloseSession}</li>
     *   <li>{@link CopyConfig}</li>
     *   <li>{@link DeleteConfig}</li>
     *   <li>{@link EditConfig}</li>
     *   <li>{@link Get}</li>
     *   <li>{@link GetConfig}</li>
     *   <li>{@link KillSession}</li>
     *   <li>{@link Lock}</li>
     *   <li>{@link Unlock}</li>
     * </ul>
     *
     * <p>The support for other RPCs is advertized through {@link #supportedExtensions()}.
     *
     * @param type QName of the RPC to be invoked
     * @param input Input arguments, null if the RPC does not take any.
     * @return A {@link ListenableFuture} which will return either a result structure, or report a subclass
     *         of {@link DOMRpcException} reporting a transport error.
     */
    @NonNull ListenableFuture<? extends DOMRpcResult> invokeNetconf(@NonNull QName type, @NonNull ContainerNode input);

    /**
     * Extensions to {@link NetconfRpcService} defining additional RPC availability.
     */
    // Note: This is not an interface on purpose, to make the set of extensions well-known
    enum Extension implements DOMService.Extension<NetconfRpcService, Extension> {
        /**
         * This device supports
         * <a href="https://www.rfc-editor.org/rfc/rfc4741#section-8.3">Candidate Configuration Capability</a>.
         * The following RPCs are supported:
         * <ul>
         *   <li>{@link Commit}</li>
         *   <li>{@link DiscardChanges}</li>
         * </ul>
         */
        CANDIDATE,
        /**
         * This device supports
         * <a href="https://www.rfc-editor.org/rfc/rfc4741#section-8.4">Confirmed Commit Capability 1.0</a>.
         * The following RPCs are supported:
         * <ul>
         *   <li>{@link Commit}</li>
         *   <li>{@link DiscardChanges}</li>
         * </ul>
         */
        CONFIRMED_COMMIT_1_0,
        /**
         * This device supports
         * <a href="https://www.rfc-editor.org/rfc/rfc6241#section-8.4">Confirmed Commit Capability 1.1</a>.
         * The following RPCs are supported:
         * <ul>
         *   <li>{@link CancelCommit}</li>
         *   <li>{@link Commit}</li>
         *   <li>{@link DiscardChanges}</li>
         * </ul>
         */
        CONFIRMED_COMMIT_1_1,
        /**
         * This device supports <a href="https://www.rfc-editor.org/rfc/rfc6022">NETCONF Monitoring</a>. The following
         * RPCs are supported:
         * <ul>
         *   <li>{@link GetSchema}</li>
         * </ul>
         */
        MONITORING,
        /**
         * This device supports <a href="https://www.rfc-editor.org/rfc/rfc5277">Event Notifications</a>. The following
         * RPCs are supported:
         * <ul>
         *   <li>{@link CreateSubscription}</li>
         * </ul>
         * {@link CreateSubscription} is supported.
         */
        NOTIFICATIONS,
        /**
         * This device supports <a href="https://www.rfc-editor.org/rfc/rfc4741#section-8.6">Validate Capability</a>.
         * The following RPCs are supported:
         * <ul>
         *   <li>{@link Validate}</li>
         * </ul>
         */
        VALIDATE_1_0,
        /**
         * This device supports <a href="https://www.rfc-editor.org/rfc/rfc6241#section-8.6">Validate Capability</a>.
         * The following RPCs are supported:
         * <ul>
         *   <li>{@link Validate}</li>
         * </ul>
         */
        VALIDATE_1_1;
    }
}
