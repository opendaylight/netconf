/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.web;

/**
 * Registers the web components for the restconf bierman-02 endpoint.
 *
 * @author Thomas Pantelis
 */
public interface Bierman02WebRegistrar {

    void registerWithAuthentication();

    void registerWithoutAuthentication();
}
