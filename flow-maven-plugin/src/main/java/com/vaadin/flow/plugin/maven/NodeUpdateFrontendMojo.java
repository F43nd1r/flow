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
package com.vaadin.flow.plugin.maven;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.frontend.FrontendUtils;
import com.vaadin.flow.server.frontend.NodeExecutor;
import org.apache.maven.model.Build;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.vaadin.flow.server.frontend.FrontendToolsLocator;

/**
 * Goal that updates Flow imports file with @JsModule, @HtmlImport and @Theme
 * annotations defined in the classpath.
 */
@Mojo(name = "update-frontend", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class NodeUpdateFrontendMojo extends NodeUpdateAbstractMojo {

    /**
     * Whether to generate a bundle from the project frontend sources or not.
     */
    @Parameter(defaultValue = "true")
    private boolean generateBundle;

    /**
     * Copy the `webapp.config.js` from the specified URL if missing. Default is
     * the template provided by this plugin. Leave it blank to disable the
     * feature.
     */
    @Parameter(defaultValue = FrontendUtils.WEBPACK_CONFIG)
    private String webpackTemplate;

    @Override
    protected Command getUpdater() {
        if (updater == null) {

            File webpackOutputRelativeToProjectDir = project.getBasedir()
                    .toPath().relativize(getWebpackOutputDirectory().toPath())
                    .toFile();
            
            updater = new NodeExecutor.Builder(getClassFinder(project),
                    frontendDirectory, generatedFlowImports, npmFolder,
                    nodeModulesPath, convertHtml)
                            .setWebpack(webpackOutputRelativeToProjectDir,
                                    webpackTemplate)
                            .build();
        }
        return updater;
    }

    @Override
    public void execute() {
        super.execute();

        if (generateBundle) {
            runWebpack();
        }
    }

    private void runWebpack() {
        File webpackExecutable = new File(nodeModulesPath, ".bin/webpack");
        if (!webpackExecutable.isFile()) {
            throw new IllegalStateException(String.format(
                    "Unable to locate webpack executable by path '%s'. Double check that the plugin us executed correctly",
                    webpackExecutable.getAbsolutePath()));
        }

        FrontendToolsLocator frontendToolsLocator = new FrontendToolsLocator();
        File nodePath = Optional.of(new File("./node/node"))
                .filter(frontendToolsLocator::verifyTool)
                .orElseGet(() -> frontendToolsLocator.tryLocateTool("node")
                        .orElseThrow(() -> new IllegalStateException(
                                "Failed to determine 'node' tool. "
                                        + "Please install it using the https://nodejs.org/en/download/ guide.")));

        Process webpackLaunch = null;
        try {
            webpackLaunch = new ProcessBuilder(nodePath.getAbsolutePath(),
                    webpackExecutable.getAbsolutePath())
                            .directory(project.getBasedir())
                            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                            .start();
            int errorCode = webpackLaunch.waitFor();
            if (errorCode != 0) {
                readDetailsAndThrowException(webpackLaunch);
            }
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(
                    "Failed to run webpack due to an error", e);
        } finally {
            if (webpackLaunch != null) {
                webpackLaunch.destroyForcibly();
            }
        }
    }

    private void readDetailsAndThrowException(Process webpackLaunch) {
        String stderr = readFullyAndClose(
                "Failed to read webpack process stderr",
                webpackLaunch::getErrorStream);
        throw new IllegalStateException(String.format(
                "Webpack process exited with non-zero exit code.%nStderr: '%s'",
                stderr));
    }

    private String readFullyAndClose(String readErrorMessage,
            Supplier<InputStream> inputStreamSupplier) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                inputStreamSupplier.get(), StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(readErrorMessage, e);
        }
    }

    private File getWebpackOutputDirectory() {
        Build buildInformation = project.getBuild();
        switch (project.getPackaging()) {
            case "jar":
                return new File(buildInformation.getOutputDirectory(),
                        "META-INF/resources");
            case "war":
                return new File(buildInformation.getDirectory(),
                        buildInformation.getFinalName());
            default:
                throw new IllegalStateException(String.format(
                        "Unsupported packaging '%s'", project.getPackaging()));
        }
    }

}
