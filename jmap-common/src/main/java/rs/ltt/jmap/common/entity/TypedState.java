/*
 * Copyright 2019 Daniel Gultsch
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rs.ltt.jmap.common.entity;

import com.google.common.base.Objects;

public class TypedState<T extends AbstractIdentifiableEntity> {

    private final String state;

    private TypedState(String state) {
        this.state = state;
    }

    public String getState() {
        return this.state;
    }

    public static <T extends AbstractIdentifiableEntity> TypedState<T> of(String state) {
        return new TypedState<>(state);
    }

    @Override
    public String toString() {
        return state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypedState<?> that = (TypedState<?>) o;
        return Objects.equal(state, that.state);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(state);
    }
}
