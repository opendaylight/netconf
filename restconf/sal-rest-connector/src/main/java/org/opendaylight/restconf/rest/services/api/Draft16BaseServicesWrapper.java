/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.services.api;

/**
 * Wrapper for all base services:
 * <ul>
 * <li>{@link RestconfModulesService}
 * <li>{@link RestconfOperationsService}
 * <li>{@link RestconfStreamsService}
 * <li>{@link RestconfSchemaService}
 * </ul>
 *
 */
public interface Draft16BaseServicesWrapper
        extends RestconfModulesService, RestconfOperationsService, RestconfStreamsService, RestconfSchemaService {
}
