/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.monitoring;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Collections2;
import java.util.Collection;
import javax.xml.bind.annotation.XmlElement;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;

final class MonitoringSchema {

    private final Schema schema;

    MonitoringSchema(final Schema schema) {
        this.schema = schema;
    }

    @XmlElement(name = "identifier")
    public String getIdentifier() {
        return schema.getIdentifier();
    }

    @XmlElement(name = "namespace")
    public String getNamespace() {
        return schema.getNamespace().getValue();
    }

    @XmlElement(name = "location")
    public Collection<String> getLocation() {
        return Collections2.transform(schema.getLocation(), input -> input.getEnumeration().toString());
    }

    @XmlElement(name = "version")
    public String getVersion() {
        return schema.getVersion();
    }

    @XmlElement(name = "format")
    public String getFormat() {
        final var format = schema.getFormat();
        checkState(Yang.VALUE.equals(format), "Only yang format permitted, but was %s", format);
        return "yang";
    }
}