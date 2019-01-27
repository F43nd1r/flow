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
 * Interface to be implemented when creating a dragend listener on a drag source
 * for HTML5 drag and drop.
 *
 * @param <T>
 *            Type of draggable component.
 * @see DragSource#addDragEndListener(DragEndListener)
 * @author Vaadin Ltd
 * @since
 */
@FunctionalInterface
public interface DragEndListener<T extends Component>
        extends ComponentEventListener<DragEndEvent<T>> {
    /**
     * Called when dragend event is fired.
     *
     * @param event
     *            Server side dragend event.
     */
    void dragEnd(DragEndEvent<T> event);

    @Override
    default void onComponentEvent(DragEndEvent<T> event) {
        dragEnd(event);
    }
}
