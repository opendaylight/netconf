/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Iterators;
import java.util.AbstractCollection;
import java.util.Iterator;
import org.eclipse.jdt.annotation.NonNull;

final class SiblingCollection extends AbstractCollection<@NonNull Sibling> {
    private final SiblingSet set;

    SiblingCollection(final SiblingSet set) {
        this.set = requireNonNull(set);
    }

    @Override
    public boolean isEmpty() {
        return set.contentMatches().isEmpty() && set.containments().isEmpty() && set.selections().isEmpty();
    }

    @Override
    public int size() {
        return set.contentMatches().size() + set.containments().size() + set.selections().size();
    }

    @Override
    public Iterator<@NonNull Sibling> iterator() {
        return Iterators.concat(
            set.contentMatches().iterator(),
            set.containments().iterator(),
            set.selections().iterator());
    }
}
