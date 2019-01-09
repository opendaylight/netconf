/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.web;

/**
 * Registers the web components. This interface serves as a utility and should be extended for each RESTCONF
 * implementation.
 *
 * @author Thomas Pantelis
 */
public interface WebRegistrar {

    void registerWithAuthentication();

    void registerWithoutAuthentication();
}
