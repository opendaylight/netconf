/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.XMLNamespace;

/**
 * A <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.5">Content Match Node</a>.
 */
@NonNullByDefault
public final class ContentMatchNode extends Sibling implements StringMatch {
    private final String value;

    ContentMatchNode(final String localName, final @Nullable XMLNamespace namespace, final String value) {
        super(localName, namespace);
        this.value = requireNonNull(value);
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    int contributeHashCode() {
        return value.hashCode();
    }

    @Override
    boolean contributeEquals(final AbstractNamespaceSelection other) {
        return value.equals(((ContentMatchNode) other).value);
    }

    @Override
    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper).add("value", value);
    }

}
