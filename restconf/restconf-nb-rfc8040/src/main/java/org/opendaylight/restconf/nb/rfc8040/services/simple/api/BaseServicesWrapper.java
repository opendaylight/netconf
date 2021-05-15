/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.simple.api;

import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfOperationsService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfSchemaService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfService;

/**
 * Wrapper for all base services.
 * <ul>
 *   <li>{@link RestconfOperationsService}</li>
 *   <li>{@link RestconfSchemaService}</li>
 * </ul>
 */
public interface BaseServicesWrapper extends RestconfOperationsService, RestconfSchemaService, RestconfService {

}
