/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.nmda.rev190107;

import java.util.Optional;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.nmda.rev190107.GetDataInput.MaxDepth;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.nmda.rev190107.GetDataInput.MaxDepth.Enumeration;
import org.opendaylight.yangtools.yang.common.Uint16;

/**
 * MaxDepth utilities.
 */
public final class GetDataInputMaxDepthBuilder {
    private GetDataInputMaxDepthBuilder() {
        // Exists only to defeat instantiation.
    }

    public static MaxDepth getDefaultInstance(final String defaultValue) {
        final Optional<Enumeration> optEnum = Enumeration.forName(defaultValue);
        if (optEnum.isPresent()) {
            return new MaxDepth(optEnum.get());
        }
        // FIXME: consider being stricter about number formats here
        return new MaxDepth(Uint16.valueOf(defaultValue));
    }
}
