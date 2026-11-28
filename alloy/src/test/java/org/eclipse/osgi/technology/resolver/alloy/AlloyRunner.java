/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.resolver.alloy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Util;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Module;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod;

/**
 * Solves the model of a scenario. The model is passed to Alloy as a string,
 * it is only written to disk if the system property {@value #DUMP_PROPERTY}
 * names a directory.
 */
class AlloyRunner {

    /** The system property with the directory the models are dumped to. */
    static final String DUMP_PROPERTY = "alloy.dump";

    private static final List<String> MODEL_FILES = List.of("resolver.als", "module_layer.als", "versions.als",
            "semantic_versions.als", "matching.als");

    private static final String VIRTUAL_DIR = "/alloy-model/";

    private final Module module;

    AlloyRunner(String name, String model) {
        try {
            Map<String, String> core = new LinkedHashMap<>();
            for (String file : MODEL_FILES) {
                core.put(file, read(file));
            }
            String dump = System.getProperty(DUMP_PROPERTY);
            if (dump != null) {
                Path dir = Path.of(dump);
                Files.createDirectories(dir);
                for (Map.Entry<String, String> e : core.entrySet()) {
                    Files.writeString(dir.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
                }
                Files.writeString(dir.resolve(name + ".als"), model, StandardCharsets.UTF_8);
            }
            // Alloy reads the files from this map before it looks at the file system
            Map<String, String> files = new HashMap<>();
            core.forEach((file, content) -> files.put(Util.canon(VIRTUAL_DIR + file), content));
            files.put(Util.canon(VIRTUAL_DIR + name + ".als"), model);
            module = CompUtil.parseEverything_fromFile(A4Reporter.NOP, files, VIRTUAL_DIR + name + ".als");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new IllegalStateException("The generated model " + name + " is not valid: " + e.getMessage(), e);
        }
    }

    private static String read(String file) throws IOException {
        try (InputStream in = AlloyRunner.class.getResourceAsStream("/" + file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * @param command the label of the command
     * @return whether the command has an instance (a counterexample for a {@code check})
     */
    boolean satisfiable(String command) {
        for (Command c : module.getAllCommands()) {
            if (c.label.equals(command)) {
                return satisfiable(c);
            }
        }
        throw new IllegalArgumentException("No command " + command);
    }

    boolean satisfiable(Command command) {
        try {
            return TranslateAlloyToKodkod.execute_command(A4Reporter.NOP, module.getAllReachableSigs(), command,
                    new A4Options()).satisfiable();
        } catch (Exception e) {
            throw new IllegalStateException("Alloy failed on " + command.label + ": " + e.getMessage(), e);
        }
    }

    /** @return the commands of the root file */
    List<Command> commands() {
        return module.getAllCommands();
    }

    /**
     * @param file the name of a file in {@code explore} without the extension
     * @return the runner for the commands of the file
     */
    static AlloyRunner explore(String file) {
        try {
            return new AlloyRunner(file, read("explore/" + file + ".als"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
