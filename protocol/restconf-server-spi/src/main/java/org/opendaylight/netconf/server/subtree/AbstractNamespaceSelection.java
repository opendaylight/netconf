/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Root for {@link NamespaceSelection} implementation classes.
 */
abstract sealed class AbstractNamespaceSelection implements NamespaceSelection permits AttributeMatch, Sibling {
    private final @NonNull String name;

    AbstractNamespaceSelection(final @NonNull String name) {
        this.name = requireNonNull(name);
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name, namespace()) * 31 + contributeHashCode();
    }

    abstract int contributeHashCode();

    @Override
    public final boolean equals(final @Nullable Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final var other = (AbstractNamespaceSelection) obj;
        return name.equals(other.name) && contributeEquals(other);
    }

    abstract boolean contributeEquals(@NonNull AbstractNamespaceSelection other);

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    ToStringHelper addToStringAttributes(final @NonNull ToStringHelper helper) {
        return helper.add("name", name).add("namespace", namespace());
    }
}
