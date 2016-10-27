/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import com.google.common.collect.Lists;
import java.io.Serializable;
import java.util.List;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class SchemaPathMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    // Schema types parameters
    private final List<QName> path;
    private final boolean absolute;

    public SchemaPathMessage(final SchemaPath type) {
        this.path = Lists.newArrayList(type.getPathTowardsRoot());
        this.absolute = type.isAbsolute();
    }

    public List<QName> getPath() {
        return path;
    }

    public boolean isAbsolute() {
        return absolute;
    }

    public SchemaPath getSchemaPath() {
        return SchemaPath.create(path, absolute);
    }
}
