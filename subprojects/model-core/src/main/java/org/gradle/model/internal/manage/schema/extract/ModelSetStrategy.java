/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.model.internal.manage.schema.extract;

import net.jcip.annotations.ThreadSafe;
import org.gradle.model.ModelSet;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.ManagedChildNodeCreatorStrategy;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

@ThreadSafe
public class ModelSetStrategy extends SetStrategy {

    public ModelSetStrategy() {
        super(new ModelType<ModelSet<?>>() {
        });
    }

    @Override
    protected <E> ModelProjection getProjection(ModelType<E> elementType, ModelSchemaStore schemaStore, NodeInitializerRegistry nodeInitializerRegistry) {
        return TypedModelProjection.of(
            ModelTypes.modelSet(elementType),
            new ModelSetModelViewFactory<E>(elementType, schemaStore, nodeInitializerRegistry)
        );
    }

    private static class ModelSetModelViewFactory<T> implements ModelViewFactory<ModelSet<T>> {
        private final ModelType<T> elementType;
        private final ModelSchemaStore store;
        private final NodeInitializerRegistry nodeInitializerRegistry;

        public ModelSetModelViewFactory(ModelType<T> elementType, ModelSchemaStore store, NodeInitializerRegistry nodeInitializerRegistry) {
            this.elementType = elementType;
            this.store = store;
            this.nodeInitializerRegistry = nodeInitializerRegistry;
        }

        @Override
        public ModelView<ModelSet<T>> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
            ModelType<ModelSet<T>> setType = ModelTypes.modelSet(elementType);
            DefaultModelViewState state = new DefaultModelViewState(setType, ruleDescriptor, writable, !writable);
            final ManagedChildNodeCreatorStrategy<T> childCreator = new ManagedChildNodeCreatorStrategy<T>(nodeInitializerRegistry);
            NodeBackedModelSet<T> set = new NodeBackedModelSet<T>(setType.toString() + " '" + modelNode.getPath() + "'", elementType, ruleDescriptor, modelNode, state, childCreator);
            return InstanceModelView.of(modelNode.getPath(), setType, set, state.closer());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ModelSetModelViewFactory<?> that = (ModelSetModelViewFactory<?>) o;
            return elementType.equals(that.elementType);

        }

        @Override
        public int hashCode() {
            return elementType.hashCode();
        }
    }


}
