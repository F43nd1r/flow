/*
 * Copyright 2000-2016 Vaadin Ltd.
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
package com.vaadin.hummingbird.template;

import com.vaadin.hummingbird.JsonCodec;
import com.vaadin.hummingbird.StateNode;

import elemental.json.Json;
import elemental.json.JsonObject;
import elemental.json.JsonValue;

/**
 * A template binding that always produces the same value.
 *
 * @author Vaadin Ltd
 */
public class StaticBinding implements TemplateBinding {
    /**
     * Type identifier used for static bindings in JSON messages.
     */
    public static final String TYPE = "static";

    private final String value;

    /**
     * Creates a binding with the given value.
     *
     * @param value
     *            the value of the binding
     */
    public StaticBinding(String value) {
        this.value = value;
    }

    @Override
    public String getValue(StateNode node) {
        return value;
    }

    @Override
    public JsonValue toJson() {
        JsonObject json = Json.createObject();

        json.put("type", TYPE);
        json.put("value", JsonCodec.encodeWithoutTypeInfo(value));

        return json;
    }
}