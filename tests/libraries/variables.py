#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# These variables are considered global and immutable, so their names are in ALL_CAPS.
#
# This is the project-local variables module every controller_testlib/
# netconf_testlib consumer is expected to provide (see
# controller_testlib.variables._LazyVariables). netconf's own test suite
# needs no overrides beyond what netconf_testlib.Variables already declares,
# so this just instantiates that class directly.
#

from netconf_testlib.variables import Variables

variables = Variables()
