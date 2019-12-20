/*
 * Copyright (c) 2019 PATHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.nacm.rule.list;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.MatchallStringType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.nacm.rule.list.Rule.ModuleName;

public class RuleModuleNameBuilder {
    private static final ModuleName MATCH_ALL = new ModuleName(new MatchallStringType("*"));

    private RuleModuleNameBuilder() {
        //Exists only to defeat instantiation.
    }

    public static ModuleName getDefaultInstance(final String defaultValue) {
        return "*".equals(defaultValue) ? MATCH_ALL : new ModuleName(defaultValue);
    }
}
