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
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.XMLNamespace;

/**
 * An <a href="https://www.rfc-editor.org/rfc/rfc6241#section-6.2.2">Attribute Match Expression</a>.
 *
 * @param attribute the attribute name
 * @param namespace the namespace
 * @param value the value
 */
@NonNullByDefault
public final class AttributeMatch extends AbstractNamespaceSelection implements StringMatch {
    private final XMLNamespace namespace;
    private final String value;

    public AttributeMatch(final String name, final XMLNamespace namespace, final String value) {
        super(name);
        this.namespace = requireNonNull(namespace);
        this.value = requireNonNull(value);
    }

    @Override
    public @NonNull XMLNamespace namespace() {
        return namespace;
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
        return value.equals(((AttributeMatch) other).value);
    }

    @Override
    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper).add("value", value);
    }
}
