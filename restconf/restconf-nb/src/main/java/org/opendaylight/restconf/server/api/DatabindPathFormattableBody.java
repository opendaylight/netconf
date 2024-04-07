/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormatParameters;
import org.opendaylight.restconf.api.FormattableBody;

/**
 * A {@link FormattableBody} which has an attached {@link DatabindPath}.
 */
@NonNullByDefault
public abstract class DatabindPathFormattableBody<P extends DatabindPath> extends FormattableBody {
    private final @NonNull P path;

    protected DatabindPathFormattableBody(final FormatParameters format, final P path) {
        super(format);
        this.path = requireNonNull(path);
    }

    public final P path() {
        return path;
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper.add("path", path).add("body", bodyAttribute()));
    }

    protected abstract @Nullable Object bodyAttribute();
}
