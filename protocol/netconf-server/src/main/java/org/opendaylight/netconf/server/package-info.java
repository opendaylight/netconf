/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * NETCONF server library. This package provides core concept specializations like {@link NetconfServerSession},
 * {@link NetconfServerSessionNegotiator} and {@link NetconfServerSessionNegotiatorFactory} which work in tandem with
 * {@code transport-api} constructs.
 *
 * <p>
 * Given a particular transport stack, the main entrypoint is {@link ServerTransportInitializer}, which is suitable
 * for passing down to the various TransportStack implementations and deals with negotiating the details of a NETCONF
 * session.
 */
package org.opendaylight.netconf.server;