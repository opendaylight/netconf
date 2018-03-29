/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.scale.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

@SuppressFBWarnings("DM_EXIT")
public class ScaleUtilParameters {

    @Arg(dest = "distro-folder")
    public File distroFolder;

    static ArgumentParser getParser() {
        final ArgumentParser parser = ArgumentParsers.newArgumentParser("scale test helper");

        parser.addArgument("--distribution-folder")
                .type(File.class)
                .help("Directory where the karaf distribution for controller is located")
                .dest("distro-folder");

        return parser;
    }

    static ScaleUtilParameters parseArgs(final String[] args, final ArgumentParser parser) {
        final ScaleUtilParameters parameters = new ScaleUtilParameters();
        try {
            parser.parseArgs(args, parameters);
            return parameters;
        } catch (ArgumentParserException e) {
            parser.handleError(e);
        }

        System.exit(1);
        return null;
    }

}
