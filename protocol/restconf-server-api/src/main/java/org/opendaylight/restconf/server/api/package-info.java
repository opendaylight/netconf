/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Interface to a RESTCONF server instance. The primary entry point is {@link RestconfServer}, which is typically given
 * a {@link ServerRequest} coupled with an {@link org.opendaylight.restconf.api.ApiPath} and perhaps a
 * {@link RequestBody}.
 */
@org.osgi.annotation.bundle.Export
package org.opendaylight.restconf.server.api;