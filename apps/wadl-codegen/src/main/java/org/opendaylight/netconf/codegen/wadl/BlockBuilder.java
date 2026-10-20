/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codegen.wadl;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.blk.AbstractBlockBuilder;
import org.opendaylight.yangtools.yang.common.XMLNamespace;

@NonNullByDefault
final class BlockBuilder extends AbstractBlockBuilder<BlockBuilder> {
    @Override
    protected BlockBuilder self() {
        return this;
    }

    BlockBuilder attr(final String name, final String value) {
        return sp().str(name).str("=\"").str(value).str("\"");
    }

    BlockBuilder attr(final String name, final BlockFragment value) {
        return sp().str(name).str("=\"").frg(value).str("\"");
    }

    BlockBuilder xmlns(final String prefix, final XMLNamespace namespace) {
        return sp().str("xmlns:").str(prefix).str("=\"").str(namespace.toString()).str("\"");
    }

    BlockBuilder elem(final String name, final BlockFragment body) {
        return oe(name).oB().frg(body).cE(name);
    }

    BlockBuilder oe(final String name) {
        return lt().str(name);
    }

    BlockBuilder oE(final String name) {
        return oe(name).oB();
    }

    BlockBuilder oB() {
        markNl(incrementIndent(buf().append(">\n")));
        return this;
    }

    BlockBuilder cE() {
        return eol("/>");
    }

    BlockBuilder cE(final String name) {
        markNl(decrementIndent().append("</").append(name).append(">\n"));
        return this;
    }

}
