/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.ServerRequest;

/**
 * A resource which supports HTTP GET and produces a {@link FormattableBody}.
 */
@NonNullByDefault
public interface HttpGetResource {

    void httpGET(ServerRequest<FormattableBody> request);

    void httpGET(ServerRequest<FormattableBody> request, ApiPath apiPath);
}
