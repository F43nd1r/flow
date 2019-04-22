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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.littemplate.LitTemplateDataAnalyzer.LitParserData;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.dom.ShadowRoot;
import com.vaadin.flow.internal.ReflectTools;
import com.vaadin.flow.internal.ReflectionCache;
import com.vaadin.flow.internal.nodefeature.NodeProperties;
import com.vaadin.flow.internal.nodefeature.VirtualChildrenList;
import com.vaadin.flow.server.VaadinService;

import elemental.json.JsonArray;

/**
 * Template initialization related logic (parse template, create sub-templates,
 * inject elements by id).
 *
 * @author Vaadin Ltd
 * @since 1.0
 *
 */
public class LitTemplateInitializer {
    private static final ReflectionCache<LitTemplate<?>, LitParserData> CACHE = new ReflectionCache<>(
            templateClass -> {
                return new LitTemplateDataAnalyzer(templateClass)
                        .parseTemplate();
            });

    private final LitTemplate<?> template;
    private final Class<? extends LitTemplate<?>> templateClass;

    private final LitParserData parserData;

    private final Map<String, Element> registeredElementIdToInjected = new HashMap<>();

    /**
     * Creates a new initializer instance.
     *
     * @param template
     *            a template to initialize
     * @param service
     *            the related service
     */
    @SuppressWarnings("unchecked")
    public LitTemplateInitializer(LitTemplate<?> template,
            VaadinService service) {
        this.template = template;

        boolean productionMode = service.getDeploymentConfiguration()
                .isProductionMode();

        templateClass = (Class<? extends LitTemplate<?>>) template.getClass();

        LitParserData data = null;
        if (productionMode) {
            data = CACHE.get(templateClass);
        }
        if (data == null) {
            data = new LitTemplateDataAnalyzer(templateClass).parseTemplate();
        }
        parserData = data;
    }

    /**
     * Initializes child elements.
     */
    public void initChildElements() {
        registeredElementIdToInjected.clear();
        mapComponents();
    }

    private void doRequestAttachCustomElement(String id, String tag,
            JsonArray path) {
        if (registeredElementIdToInjected.containsKey(id)) {
            return;
        }
        // make sure that shadow root is available
        getShadowRoot();

        Element element = new Element(tag);
        VirtualChildrenList list = getElement().getNode()
                .getFeature(VirtualChildrenList.class);
        list.append(element.getNode(), NodeProperties.TEMPLATE_IN_TEMPLATE,
                path);
    }

    private ShadowRoot getShadowRoot() {
        return getElement().getShadowRoot()
                .orElseGet(() -> getElement().attachShadow());
    }

    /* Map declared fields marked @Id */

    private void mapComponents() {
        parserData.forEachInjectedField(this::tryMapComponentOrElement);
    }

    private void tryMapComponentOrElement(Field field, String id, String tag) {
        Element element = getElementById(id).orElse(null);

        if (element == null) {
            injectClientSideElement(id, field);
        } else {
            injectServerSideElement(element, field);
        }
    }

    private void injectServerSideElement(Element element, Field field) {
        if (getElement().equals(element)) {
            throw new IllegalArgumentException(
                    "Cannot map the root element of the template. "
                            + "This is always mapped to the template instance itself ("
                            + templateClass.getName() + ')');
        } else if (element != null) {
            injectTemplateElement(element, field);
        }
    }

    private void injectClientSideElement(String id, Field field) {
        Class<?> fieldType = field.getType();

        Tag tag = fieldType.getAnnotation(Tag.class);
        String tagName = tag.value();
        attachExistingElementById(tagName, id, field);
    }

    private Optional<Element> getElementById(String id) {
        return getShadowRoot().getChildren().flatMap(this::flattenChildren)
                .filter(element -> id.equals(element.getAttribute("id")))
                .findFirst();
    }

    private Stream<Element> flattenChildren(Element node) {
        if (node.getChildCount() > 0) {
            return node.getChildren().flatMap(this::flattenChildren);
        }
        return Stream.of(node);
    }

    /**
     * Attaches a child element with the given {@code tagName} and {@code id} to
     * an existing dom element on the client side with matching data.
     *
     * @param tagName
     *            tag name of element, not {@code null}
     * @param id
     *            id of element to attach to
     * @param field
     *            field to attach {@code Element} or {@code Component} to
     */
    private void attachExistingElementById(String tagName, String id,
            Field field) {
        if (tagName == null) {
            throw new IllegalArgumentException(
                    "Tag name parameter cannot be null");
        }

        Element element = registeredElementIdToInjected.get(id);
        if (element == null) {
            element = new Element(tagName);
            VirtualChildrenList list = getElement().getNode()
                    .getFeature(VirtualChildrenList.class);
            list.append(element.getNode(), NodeProperties.INJECT_BY_ID, id);
            registeredElementIdToInjected.put(id, element);
        }
        injectTemplateElement(element, field);
    }

    private Element getElement() {
        return template.getElement();
    }

    @SuppressWarnings("unchecked")
    private void injectTemplateElement(Element element, Field field) {
        Class<?> fieldType = field.getType();
        if (Component.class.isAssignableFrom(fieldType)) {
            Component component;

            Optional<Component> wrappedComponent = element.getComponent();
            if (wrappedComponent.isPresent()) {
                component = wrappedComponent.get();
            } else {
                Class<? extends Component> componentType = (Class<? extends Component>) fieldType;
                component = Component.from(element, componentType);
            }

            ReflectTools.setJavaFieldValue(template, field, component);
        } else if (Element.class.isAssignableFrom(fieldType)) {
            ReflectTools.setJavaFieldValue(template, field, element);
        } else {
            String msg = String.format(
                    "The field '%s' in '%s' has an @'%s' "
                            + "annotation but the field type '%s' "
                            + "does not extend neither '%s' nor '%s'",
                    field.getName(), templateClass.getName(),
                    Id.class.getSimpleName(), fieldType.getName(),
                    Component.class.getSimpleName(),
                    Element.class.getSimpleName());

            throw new IllegalArgumentException(msg);
        }
    }

}
