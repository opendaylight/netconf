/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops.parser;

import java.io.File;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.netconf.mdsal.connector.ops.Datastore;

public class MdsalNetconfParameter {
    private MdsalNetconfParameterType type;
    private Object value;

    public Datastore getDatastore() {
        return (value != null && type == MdsalNetconfParameterType.DATASTORE) ? (Datastore)value : null;
    }

    public XmlElement getConfigElement() {
        return (value != null && type == MdsalNetconfParameterType.CONFIG) ? (XmlElement)value : null;
    }

    public File getFile() {
        return (value != null && type == MdsalNetconfParameterType.FILE) ? (File)value : null;
    }

    public MdsalNetconfParameter(Datastore datastore) {
        this.value = datastore;
        this.type = MdsalNetconfParameterType.DATASTORE;
    }

    public MdsalNetconfParameter(XmlElement configElement) {
        this.value = configElement;
        this.type = MdsalNetconfParameterType.CONFIG;
    }

    public MdsalNetconfParameter(File file) {
        this.value = file;
        this.type = MdsalNetconfParameterType.FILE;
    }

    public MdsalNetconfParameterType getType() {
        return type;
    }
}
