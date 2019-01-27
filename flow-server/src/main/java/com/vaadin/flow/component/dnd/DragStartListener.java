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
package com.vaadin.flow.component.dnd;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;

/**
 * Interface to be implemented when creating a dragstart listener on a drag
 * source for HTML5 drag and drop.
 *
 * @param <T>
 *            Type of draggable component.
 * @author Vaadin Ltd
 * @see DragSource#addDragStartListener(DragStartListener)
 * @since
 */
@FunctionalInterface
public interface DragStartListener<T extends Component>
        extends ComponentEventListener<DragStartEvent<T>> {
    /**
     * Called when dragstart event is fired.
     *
     * @param event
     *            Server side dragstart event.
     */
    void dragStart(DragStartEvent<T> event);

    @Override
    default void onComponentEvent(DragStartEvent<T> event) {
        dragStart(event);
    }
}
