/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.osgi;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.NetconfServerSession;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.operations.HandlingPriority;
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationChainedExecution;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.SessionAwareNetconfOperation;
import org.opendaylight.netconf.server.mapping.operations.DefaultCloseSession;
import org.opendaylight.netconf.server.mapping.operations.DefaultNetconfOperation;
import org.opendaylight.netconf.server.mapping.operations.DefaultStartExi;
import org.opendaylight.netconf.server.mapping.operations.DefaultStopExi;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

// Non-final for testing
public class NetconfOperationRouterImpl implements NetconfOperationRouter, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfOperationRouterImpl.class);

    private final NetconfOperationService netconfOperationServiceSnapshot;
    private final Collection<NetconfOperation> allNetconfOperations;

    public NetconfOperationRouterImpl(final NetconfOperationService netconfOperationServiceSnapshot,
            final NetconfMonitoringService netconfMonitoringService, final SessionIdType sessionId) {
        this.netconfOperationServiceSnapshot = requireNonNull(netconfOperationServiceSnapshot);

        final Set<NetconfOperation> ops = new HashSet<>();
        ops.add(new DefaultCloseSession(sessionId, this));
        ops.add(new DefaultStartExi(sessionId));
        ops.add(new DefaultStopExi(sessionId));

        ops.addAll(netconfOperationServiceSnapshot.getNetconfOperations());

        allNetconfOperations = ImmutableSet.copyOf(ops);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public Document onNetconfMessage(final Document message, final NetconfServerSession session)
            throws DocumentedException {
        requireNonNull(allNetconfOperations, "Operation router was not initialized properly");

        final NetconfOperationExecution netconfOperationExecution;
        try {
            netconfOperationExecution = getNetconfOperationWithHighestPriority(message, session);
        } catch (IllegalArgumentException | IllegalStateException e) {
            final String messageAsString = XmlUtil.toString(message);
            LOG.warn("Unable to handle rpc {} on session {}", messageAsString, session, e);

            final ErrorTag tag = e instanceof IllegalArgumentException ? ErrorTag.OPERATION_NOT_SUPPORTED
                : ErrorTag.OPERATION_FAILED;

            throw new DocumentedException(
                    String.format("Unable to handle rpc %s on session %s", messageAsString, session), e,
                    ErrorType.APPLICATION, tag, ErrorSeverity.ERROR,
                    // FIXME: i.e. in what namespace are we providing these tags? why is this not just:
                    //
                    // <java-throwable xmlns="org.opendaylight.something">
                    //   <message>e.getMessage()</message>
                    // </java-throwable>
                    //
                    // for each place where we are mapping Exception.getMessage() ? We probably do not want to propagate
                    // stack traces out, but suppressed exceptions and causal list might be interesting:
                    //
                    // <java-throwable xmlns="org.opendaylight.something">
                    //   <message>reported exception</message>
                    // </java-throwable>
                    // <java-throwable xmlns="org.opendaylight.something">
                    //   <message>cause of reported exception</message>
                    // </java-throwable>
                    // <java-throwable xmlns="org.opendaylight.something">
                    //   <message>cause of cause of reported exception</message>
                    // </java-throwable>
                    Map.of(tag.elementBody(), e.getMessage()));
        } catch (final RuntimeException e) {
            throw handleUnexpectedEx("sort", e);
        }

        try {
            return executeOperationWithHighestPriority(message, netconfOperationExecution);
        } catch (final RuntimeException e) {
            throw handleUnexpectedEx("execution", e);
        }
    }

    @Override
    public void close() {
        netconfOperationServiceSnapshot.close();
    }

    private static DocumentedException handleUnexpectedEx(final String op, final Exception exception) {
        LOG.error("Unexpected exception during netconf operation {}", op, exception);
        return new DocumentedException("Unexpected error",
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR,
                // FIXME: i.e. <error>exception.toString()</error>? That looks wrong on a few levels.
                Map.of(ErrorSeverity.ERROR.elementBody(), exception.toString()));
    }

    private static Document executeOperationWithHighestPriority(final Document message,
            final NetconfOperationExecution netconfOperationExecution) throws DocumentedException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Forwarding netconf message {} to {}", XmlUtil.toString(message), netconfOperationExecution
                    .netconfOperation);
        }

        return netconfOperationExecution.execute(message);
    }

    private NetconfOperationExecution getNetconfOperationWithHighestPriority(
            final Document message, final NetconfServerSession session) throws DocumentedException {

        final NavigableMap<HandlingPriority, NetconfOperation> sortedByPriority =
                getSortedNetconfOperationsWithCanHandle(
                message, session);

        if (sortedByPriority.isEmpty()) {
            throw new IllegalArgumentException(String.format("No %s available to handle message %s",
                    NetconfOperation.class.getName(), XmlUtil.toString(message)));
        }

        return NetconfOperationExecution.createExecutionChain(sortedByPriority, sortedByPriority.lastKey());
    }

    private TreeMap<HandlingPriority, NetconfOperation> getSortedNetconfOperationsWithCanHandle(
            final Document message, final NetconfServerSession session) throws DocumentedException {
        final TreeMap<HandlingPriority, NetconfOperation> sortedPriority = new TreeMap<>();

        for (final NetconfOperation netconfOperation : allNetconfOperations) {
            final HandlingPriority handlingPriority = netconfOperation.canHandle(message);
            if (netconfOperation instanceof DefaultNetconfOperation defaultOperation) {
                defaultOperation.setNetconfSession(session);
            }
            if (netconfOperation instanceof SessionAwareNetconfOperation sessionAwareOperation) {
                sessionAwareOperation.setSession(session);
            }
            if (!handlingPriority.equals(HandlingPriority.CANNOT_HANDLE)) {

                checkState(!sortedPriority.containsKey(handlingPriority),
                        "Multiple %s available to handle message %s with priority %s, %s and %s",
                        NetconfOperation.class.getName(), message, handlingPriority, netconfOperation, sortedPriority
                                .get(handlingPriority));
                sortedPriority.put(handlingPriority, netconfOperation);
            }
        }
        return sortedPriority;
    }

    private static final class NetconfOperationExecution implements NetconfOperationChainedExecution {
        private final NetconfOperation netconfOperation;
        private final NetconfOperationChainedExecution subsequentExecution;

        private NetconfOperationExecution(final NetconfOperation netconfOperation,
                                          final NetconfOperationChainedExecution subsequentExecution) {
            this.netconfOperation = netconfOperation;
            this.subsequentExecution = subsequentExecution;
        }

        @Override
        public boolean isExecutionTermination() {
            return false;
        }

        @Override
        public Document execute(final Document message) throws DocumentedException {
            return netconfOperation.handle(message, subsequentExecution);
        }

        public static NetconfOperationExecution createExecutionChain(
                final NavigableMap<HandlingPriority, NetconfOperation> sortedByPriority,
                final HandlingPriority handlingPriority) {
            final NetconfOperation netconfOperation = sortedByPriority.get(handlingPriority);
            final HandlingPriority subsequentHandlingPriority = sortedByPriority.lowerKey(handlingPriority);

            NetconfOperationChainedExecution subsequentExecution = null;

            if (subsequentHandlingPriority != null) {
                subsequentExecution = createExecutionChain(sortedByPriority, subsequentHandlingPriority);
            } else {
                subsequentExecution = EXECUTION_TERMINATION_POINT;
            }

            return new NetconfOperationExecution(netconfOperation, subsequentExecution);
        }
    }

    @Override
    public String toString() {
        return "NetconfOperationRouterImpl{" + "netconfOperationServiceSnapshot=" + netconfOperationServiceSnapshot
                + '}';
    }
}
