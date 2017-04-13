/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight;

import java.lang.reflect.Field;

public class ReflectionTestUtils {

    /**
     * Set field value using reflection.
     *
     * <p>
     * Use only as the last option.
     *
     * @param obj Object reference
     * @param name Name of field
     * @param value Value to be set
     * @throws Exception in case of reflection error.
     */
    public static void setField(final Object obj, final String name, final Object value) throws Exception {
        final Field declaredField = obj.getClass().getDeclaredField(name);
        declaredField.setAccessible(true);
        declaredField.set(obj, value);
    }

    /**
     * Set value for field declared in class parent.
     *
     * <p>
     * Use only as the last option.
     *
     * @param obj Object reference
     * @param name Name of field
     * @param value Value to be set
     * @throws Exception in case of reflection error.
     */
    public static void setInnerField(final Object obj, final String name, final Object value) throws Exception {
        final Field declaredField = obj.getClass().getSuperclass().getDeclaredField(name);
        declaredField.setAccessible(true);
        declaredField.set(obj, value);
    }
}
