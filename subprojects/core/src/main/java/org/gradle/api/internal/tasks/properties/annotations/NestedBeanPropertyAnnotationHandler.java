/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.properties.annotations;

import org.gradle.api.internal.tasks.DefaultTaskInputPropertySpec;
import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidatingValue;
import org.gradle.api.internal.tasks.ValidationAction;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.tasks.Nested;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;

import static org.gradle.api.internal.tasks.TaskValidationContext.Severity.ERROR;

public class NestedBeanPropertyAnnotationHandler implements PropertyAnnotationHandler {
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Nested.class;
    }

    @Override
    public void visitPropertyValue(final PropertyValue propertyValue, PropertyVisitor visitor, PropertySpecFactory specFactory) {
        Object nested = propertyValue.getValue();
        if (nested != null) {
            if (nested instanceof Iterable<?>) {
                Iterable nestedBeans = (Iterable) nested;
                int count = 0;
                for (Object nestedBean : nestedBeans) {
                    addNestedClassProperty(nestedBean, visitor, specFactory, propertyValue.getPropertyName() + "$" + ++count, propertyValue.isOptional());
                }
            } else {
                addNestedClassProperty(nested, visitor, specFactory, propertyValue.getPropertyName(), propertyValue.isOptional());
            }
        }
    }

    private void addNestedClassProperty(Object nestedBean, PropertyVisitor visitor, PropertySpecFactory specFactory, String propertyName, boolean optional) {
        DefaultTaskInputPropertySpec propertySpec = specFactory.createInputPropertySpec(propertyName + ".class", new NestedPropertyValue(nestedBean));
        propertySpec.optional(optional);
        visitor.visitInputProperty(propertySpec);
    }

    private static class NestedPropertyValue implements ValidatingValue {
        private final Object bean;

        public NestedPropertyValue(@Nullable Object bean) {
            this.bean = bean;
        }

        @Nullable
        @Override
        public Object call() {
            return bean == null ? null : bean.getClass().getName();
        }

        @Override
        public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
            if (bean == null) {
                if (!optional) {
                    String realPropertyName = propertyName.substring(0, propertyName.length() - ".class".length());
                    context.recordValidationMessage(ERROR, String.format("No value has been specified for property '%s'.", realPropertyName));
                }
            } else {
                valueValidator.validate(propertyName, bean, context, ERROR);
            }
        }
    }
}
