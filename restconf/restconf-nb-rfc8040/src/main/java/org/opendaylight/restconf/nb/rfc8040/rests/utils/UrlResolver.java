/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import java.net.URI;
import javax.ws.rs.core.UriInfo;

/**
 * Interface for prepare stream URL.
 *
 */
public interface UrlResolver {

    /**
     * Prepare URL from base name and stream name.
     * @param uriInfo base URL information
     * @param streamName name of stream for create
     * @return final URL
     */
    URI prepareUriByStreamName(UriInfo uriInfo, String streamName);
}
