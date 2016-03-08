/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.api.connector;

import java.util.Set;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;

public interface RestSchemaController extends SchemaContextListener, AutoCloseable {

    void setGlobalSchema(@Nonnull SchemaContext globalSchema);

    SchemaContext getGlobalSchema();

    Set<Module> getAllModules();

    DataSchemaNode getRestconfModuleRestConfSchemaNode(Module inRestconfModule, String schemaNodeName);

    Module getRestconfModule();

    InstanceIdentifierContext<?> toMountPointIdentifier(String restconfInstance);

    Module findModuleByNameAndRevision(DOMMountPoint mountPoint, QName module);

    Module findModuleByNameAndRevision(QName module);
}
