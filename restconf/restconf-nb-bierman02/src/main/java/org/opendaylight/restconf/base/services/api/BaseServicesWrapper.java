/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.base.services.api;

/**
 * {@link Deprecated} move to splitted module restconf-nb-rfc8040. Wrapper for all base services.
 * <ul>
 * <li>{@link RestconfOperationsService}
 * <li>{@link RestconfSchemaService}
 * </ul>
 *
 */
@Deprecated
public interface BaseServicesWrapper extends RestconfOperationsService, RestconfSchemaService, RestconfService {
}
