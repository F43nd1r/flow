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
package com.vaadin.flow.component.littemplate;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.polymertemplate.AbstractTemplate;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.internal.AnnotationReader;

import elemental.json.JsonArray;

/**
 * Template data analyzer which produces immutable data required for template
 * initializer using provided template class and a parser.
 *
 * @author Vaadin Ltd
 * @since 1.0
 *
 */
class LitTemplateDataAnalyzer {

    private final Class<? extends LitTemplate<?>> templateClass;
    private final String tag;

    private final Map<String, String> tagById = new HashMap<>();
    private final Map<Field, String> idByField = new HashMap<>();

    private final Set<String> notInjectableElementIds = new HashSet<>();

    /**
     * Three argument consumer.
     *
     * @author Vaadin Ltd
     * @since 1.0
     *
     */
    @FunctionalInterface
    interface InjectableFieldCunsumer {

        /**
         * Performs this operation on the given arguments.
         * <p>
         * The arguments are: the field declared in a template class, the
         * identifier of the element inside the HTML template file, the element
         * tag.
         *
         * @param field
         *            the field declared in a template class
         * @param id
         *            the element id
         * @param tag
         *            the element tag
         */
        void apply(Field field, String id, String tag);
    }

    /**
     * Immutable parser data which may be stored in cache.
     */
    static class LitParserData {

        private final Map<String, String> tagById;
        private final Map<Field, String> idByField;

        private LitParserData(Map<Field, String> fields,
                Map<String, String> tags) {
            tagById = Collections.unmodifiableMap(tags);
            idByField = Collections.unmodifiableMap(fields);
        }

        void forEachInjectedField(InjectableFieldCunsumer consumer) {
            idByField.forEach(
                    (field, id) -> consumer.apply(field, id, tagById.get(id)));
        }

    }

    static class SubTemplateData {
        private final String id;
        private final String tag;
        private final JsonArray path;

        SubTemplateData(String id, String tag, JsonArray path) {
            this.id = id;
            this.tag = tag;
            this.path = path;
        }

        String getId() {
            return id;
        }

        String getTag() {
            return tag;
        }

        JsonArray getPath() {
            return path;
        }
    }

    /**
     * Create an instance of the analyzer using the {@code templateClass} and
     * the template {@code parser}.
     *
     * @param templateClass
     *            a template type
     * @param parser
     *            a template parser
     * @param service
     *            the related service instance
     */
    LitTemplateDataAnalyzer(Class<? extends LitTemplate<?>> templateClass) {
        this.templateClass = templateClass;
        tag = getTag(templateClass);
    }

    /**
     * Gets the template data for the template initializer.
     *
     * @return the template data
     */
    LitParserData parseTemplate() {
        collectInjectedIds(templateClass);
        return readData();
    }

    private void collectInjectedIds(Class<?> cls) {
        if (!AbstractTemplate.class.equals(cls.getSuperclass())) {
            // Parent fields
            collectInjectedIds(cls.getSuperclass());
        }

        Stream.of(cls.getDeclaredFields()).filter(field -> !field.isSynthetic())
                .forEach(field -> collectedInjectedId(field));
    }

    private void collectedInjectedId(Field field) {
        Optional<Id> idAnnotation = AnnotationReader.getAnnotationFor(field,
                Id.class);
        if (!idAnnotation.isPresent()) {
            return;
        }
        String id = idAnnotation.get().value();
        boolean emptyValue = id.isEmpty();
        if (emptyValue) {
            id = field.getName();
        }
        if (notInjectableElementIds.contains(id)) {
            throw new IllegalStateException(String.format(
                    "Class '%s' contains field '%s' annotated with @Id%s. "
                            + "Corresponding element was found in a sub template, "
                            + "for which injection is not supported.",
                    templateClass.getName(), field.getName(),
                    emptyValue
                            ? " without value (so the name of the field should match the id of an element in the template)"
                            : "(\"" + id + "\")"));
        }

        idByField.put(field, id);
    }

    private LitParserData readData() {
        return new LitParserData(idByField, tagById);
    }

    private String getTag(Class<? extends LitTemplate<?>> clazz) {
        Optional<String> tagNameAnnotation = AnnotationReader
                .getAnnotationFor(clazz, Tag.class).map(Tag::value);
        assert tagNameAnnotation.isPresent();
        return tagNameAnnotation.get();
    }

}
