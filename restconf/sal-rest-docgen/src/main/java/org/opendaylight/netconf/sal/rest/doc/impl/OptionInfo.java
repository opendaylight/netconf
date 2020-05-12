/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.MandatoryAware;


public class OptionInfo {
    private List<String> propertiesNames = new ArrayList<>();
    private List<String> required = new ArrayList<>();

    public void add(final OptionInfo optionInfo) {
        propertiesNames.addAll(optionInfo.getPropertiesNames());
        required.addAll(optionInfo.getRequired());
    }

    public void addNode(final DataSchemaNode node) {
        final String name = node.getQName().getLocalName();
        propertiesNames.add(name);
        if (node instanceof MandatoryAware
                && ((MandatoryAware) node).isMandatory()) {
            required.add(name);
        }
    }

    public static OptionInfo merge(final OptionInfo info1, final OptionInfo info2) {
        final OptionInfo info = new OptionInfo();
        info.add(info1);
        info.add(info2);
        return info;
    }

    public List<String> getPropertiesNames() {
        return propertiesNames;
    }

    public void setPropertiesNames(final List<String> propertiesNames) {
        this.propertiesNames = propertiesNames;
    }

    public List<String> getRequired() {
        return required;
    }

    public void setRequired(final List<String> required) {
        this.required = required;
    }
}
