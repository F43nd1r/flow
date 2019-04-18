/*
 * Copyright 2000-2018 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.flow.server.frontend;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.component.dependency.NpmPackage;
import elemental.json.Json;
import elemental.json.JsonObject;
import org.apache.commons.io.FileUtils;

import static com.vaadin.flow.server.Constants.PACKAGE_JSON;
import static com.vaadin.flow.server.frontend.FrontendUtils.FLOW_NPM_PACKAGE_NAME;

/**
 * Updates <code>package.json</code> by visiting {@link NpmPackage} annotations found in
 * the classpath. It also visits classes annotated with {@link NpmPackage}
 */
public class NodeUpdatePackages extends NodeUpdater {

    /**
     * Create an instance of the updater given all configurable parameters.
     *
     * @param finder
     *            a reusable class finder
     * @param npmFolder
     *            folder with the `package.json` file
     * @param nodeModulesPath
     *            the path to the {@literal node_modules} directory of the
     *            project
     * @param convertHtml
     *            whether to convert html imports or not during the package
     *            updates
     */
    public NodeUpdatePackages(ClassFinder finder, File npmFolder,
                              File nodeModulesPath, boolean convertHtml) {
        this(finder, null, npmFolder, nodeModulesPath, convertHtml);
    }

    /**
     * Create an instance of the updater given all configurable parameters.
     *
     * @param finder
     *            a reusable class finder
     * @param frontendDependencies
     *            a reusable frontend dependencies
     * @param npmFolder
     *            folder with the `package.json` file
     * @param nodeModulesPath
     *            the path to the {@literal node_modules} directory of the
     *            project
     * @param convertHtml
     *            whether to convert html imports or not during the package
     *            updates
     */
    public NodeUpdatePackages(ClassFinder finder,
            FrontendDependencies frontendDependencies, File npmFolder,
            File nodeModulesPath, boolean convertHtml) {
        super(finder, frontendDependencies, npmFolder, nodeModulesPath, convertHtml);
    }

    @Override
    public void execute() {
        try {
            JsonObject packageJson = getPackageJson();

            Set<String> deps = new HashSet<>(frontDeps.getPackages());
            if (convertHtml) {
                deps.addAll(getHtmlImportNpmPackages(frontDeps.getImports()));
            }

            updatePackageJsonDependencies(packageJson, deps);
            updatePackageJsonDevDependencies(packageJson);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void updatePackageJsonDependencies(JsonObject packageJson, Set<String> classes) {
        JsonObject currentDeps = packageJson.getObject("dependencies");

        Set<String> dependencies = new HashSet<>();
        classes.forEach(s -> {
            // exclude local dependencies (those starting with `.` or `/`
            if (s.matches("[^./].*") && !s.matches("(?i)[a-z].*\\.js$") && !currentDeps.hasKey(s)
                    && !s.startsWith(FLOW_NPM_PACKAGE_NAME)) {
                dependencies.add(s);
            }
        });

        if (!currentDeps.hasKey("@webcomponents/webcomponentsjs")) {
            dependencies.add("@webcomponents/webcomponentsjs");
        }

        if (dependencies.isEmpty()) {
            log().info("No npm packages to update");
        } else {
            updateDependencies(dependencies.stream().sorted().collect(Collectors.toList()), "--save");
        }
    }

    private void updatePackageJsonDevDependencies(JsonObject packageJson) {
        JsonObject currentDeps = packageJson.getObject("devDependencies");

        Set<String> dependencies = new HashSet<>();
        dependencies.add("webpack");
        dependencies.add("webpack-cli");
        dependencies.add("webpack-dev-server");
        dependencies.add("webpack-babel-multi-target-plugin");
        dependencies.add("copy-webpack-plugin");

        dependencies.removeAll(Arrays.asList(currentDeps.keys()));

        if (dependencies.isEmpty()) {
            log().info("No npm dev packages to update");
        } else {
            updateDependencies(dependencies.stream().sorted().collect(Collectors.toList()), "--save-dev");
        }
    }

    void updateDependencies(List<String> dependencies,
            String... npmInstallArgs) {
        ProcessBuilder builder = new ProcessBuilder(
                getNpmCommand(dependencies, npmInstallArgs));
        builder.directory(npmFolder);

        if (log().isInfoEnabled()) {
            log().info(
                "Updating package.json and installing npm dependencies ...\n {}",
                String.join(" ", builder.command()));
        }

        Process process = null;
        try {
            process = builder.inheritIO().start();
            int errorCode = process.waitFor();
            if (errorCode != 0) {
                log().error(
                        ">>> Dependency ERROR. Check that all required dependencies are deployed in npm repositories.");
            } else {
                log().info(
                        "package.json updated and npm dependencies installed. ");
            }
        } catch (InterruptedException | IOException e) {
            log().error("Error running npm", e);
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    private List<String> getNpmCommand(List<String> dependencies,
            String... npmInstallArgs) {
        List<String> command = new ArrayList<>(5 + dependencies.size());
        command.addAll(FrontendUtils.getNpmExecutable());
        command.add("--no-package-lock");
        command.add("install");
        command.addAll(Arrays.asList(npmInstallArgs));
        command.addAll(dependencies);
        return command;
    }

    JsonObject getPackageJson() throws IOException {
        JsonObject packageJson;
        File packageFile = new File(npmFolder, PACKAGE_JSON);

        if (packageFile.exists()) {
            packageJson = Json.parse(FileUtils.readFileToString(packageFile, "UTF-8"));
        } else {
            log().info("Creating a default {}", packageFile);
            FileUtils.writeStringToFile(packageFile, "{}", "UTF-8");
            packageJson = Json.createObject();
        }
        ensureMissingObject(packageJson, "dependencies");
        ensureMissingObject(packageJson, "devDependencies");
        return packageJson;
    }

    private void ensureMissingObject(JsonObject packageJson, String name) {
        if (!packageJson.hasKey(name)) {
            packageJson.put(name, Json.createObject());
        }
    }
}
