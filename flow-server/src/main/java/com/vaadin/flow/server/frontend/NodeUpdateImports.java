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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.vaadin.flow.component.dependency.HtmlImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.theme.AbstractTheme;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.ThemeDefinition;

import static com.vaadin.flow.server.frontend.FrontendUtils.getBaseDir;
/**
 * An updater that it's run when the servlet context is initialised in dev-mode
 * or when flow-maven-plugin goals are run in order to update
 * <code>main.js</code> and <code>node_module/@vaadin/flow-frontend</code>
 * contents by visiting all classes with {@link JsModule} {@link HtmlImport} and
 * {@link Theme} annotations.
 */
public class NodeUpdateImports extends NodeUpdater {
    /**
     * File to be updated with imports, javascript, and theme annotations.
     * It is also the entry-point for webpack.
     */
    public static final String MAIN_JS = "frontend/main.js";
    static final String MAIN_JS_PARAM = "vaadin.frontend.jsFile";

    private final File jsFile;

    /**
     * Create an instance of the updater given all configurable parameters.
     *
     * @param finder
     *            a reusable class finder
     * @param jsFile
     *            name of the JS file to update with the imports
     * @param npmFolder
     *            folder with the `package.json` file
     * @param nodeModulesPath
     *            the path to the {@literal node_modules} directory of the project
     * @param convertHtml
     *            true to enable polymer-2 annotated classes to be considered
     */
    public NodeUpdateImports(ClassFinder finder, File jsFile, File npmFolder, File nodeModulesPath,
            boolean convertHtml) {
        this(finder, null, jsFile, npmFolder, nodeModulesPath, convertHtml);
    }


    /**
     * Create an instance of the updater given all configurable parameters.
     *
     * @param finder
     *            a reusable class finder
     * @param frontendDependencies
     *            a reusable frontend dependencies
     * @param jsFile
     *            name of the JS file to update with the imports
     * @param npmFolder
     *            folder with the `package.json` file
     * @param nodeModulesPath
     *            the path to the {@literal node_modules} directory of the project
     * @param convertHtml
     *            true to enable polymer-2 annotated classes to be considered
     */
    public NodeUpdateImports(ClassFinder finder, FrontendDependencies frontendDependencies, File jsFile, File npmFolder, File nodeModulesPath,
                             boolean convertHtml) {
        super(finder, frontendDependencies, npmFolder, nodeModulesPath, convertHtml);
        this.jsFile = jsFile;
    }

    @Override
    public void execute() {
        Set<String> modules = new HashSet<>(frontDeps.getModules());
        if (convertHtml) {
            modules.addAll(getHtmlImportJsModules(frontDeps.getImports()));
        }
        modules.addAll(getJavascriptJsModules(frontDeps.getScripts()));

        modules = sortModules(modules);
        try {
            installFlowModules();
            updateMainJsFile(getMainJsContent(modules));
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Failed to update the Flow imports file '%s'", jsFile), e);
        }
    }

    private Set<String> sortModules(Set<String> modules) {
        return modules.stream().map(this::toValidBrowserImport).sorted(Comparator.reverseOrder())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> getMainJsContent(Set<String> modules) {
        List<String> lines = new ArrayList<>();
        AbstractTheme theme = frontDeps.getTheme();
        ThemeDefinition themeDef = frontDeps.getThemeDefinition();
        if (theme != null) {
            if (!theme.getHeaderInlineContents().isEmpty()) {
                lines.add("const div = document.createElement('div');");
                theme.getHeaderInlineContents().forEach(html -> {
                    lines.add("div.innerHTML = '" + html.replaceAll("(?m)(^\\s+|\\s?\n)", "") + "';");
                    lines.add("document.head.insertBefore(div.firstElementChild, document.head.firstChild);");
                });
            }
            theme.getHtmlAttributes(themeDef.getVariant())
                    .forEach((key, value) -> lines.add("document.body.setAttribute('" + key + "', '" + value + "');"));
        }

        lines.addAll(modulesToImports(modules, theme));

        return lines;
    }

    private List<String> modulesToImports(Set<String> modules, AbstractTheme theme) {
        List<String> imports = new ArrayList<>(modules.size());
        Map<String, String> unresolvedImports = new HashMap<>(modules.size());

        for (String originalModulePath : modules) {
            String translatedModulePath = originalModulePath;
            if (theme != null && translatedModulePath.contains(theme.getBaseUrl())) {
                translatedModulePath = theme.translateUrl(translatedModulePath);
            }
            if (importedFileExists(translatedModulePath)) {
                imports.add("import '" + translatedModulePath + "';");

            } else if (importedFileExists(originalModulePath)) {
                imports.add("import '" + originalModulePath + "';");
            } else {
                unresolvedImports.put(originalModulePath, translatedModulePath);
            }
        }

        if (!unresolvedImports.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder(String.format(
                "Failed to resolve the following module imports neither in the node_modules directory '%s' " +
                    "nor in project files: ",
                nodeModulesPath)).append("\n");

            unresolvedImports
                .forEach((originalModulePath, translatedModulePath) -> {
                    errorMessage.append(
                        String.format("'%s'", translatedModulePath));
                    if (!Objects.equals(originalModulePath,
                        translatedModulePath)) {
                        errorMessage.append(String.format(
                            " (the import was translated by Flow from the path '%s')",
                            originalModulePath));
                    }
                    errorMessage.append("\n");
                });

            errorMessage.append("Double check that those files exist in the project structure.");

            throw new IllegalStateException(errorMessage.toString());
        }

        return imports;
    }

    private boolean importedFileExists(String jsImport) {
        if (jsImport.startsWith("./")) {
            return new File(jsFile.getParentFile(), jsImport).isFile();
        } else {
            return new File(nodeModulesPath, jsImport).isFile();
        }
    }

    private void updateMainJsFile(List<String> newContent) throws IOException {
        List<String> oldContent = jsFile.exists() ? FileUtils.readLines(jsFile, "UTF-8") : null;
        if (newContent.equals(oldContent)) {
            log().info("No js modules to update");
        } else {
            FileUtils.forceMkdir(jsFile.getParentFile());
            FileUtils.writeStringToFile(jsFile, String.join("\n", newContent), "UTF-8");
            log().info("Updated {}", jsFile);
        }
    }

}
