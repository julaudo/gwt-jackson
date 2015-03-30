/*
 * Copyright 2015 Nicolas Morel
 *
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
 */

package com.github.nmorel.gwtjackson.rebind;

import java.io.Writer;

import com.google.gwt.thirdparty.guava.common.base.Optional;
import com.squareup.javapoet.TypeSpec;

/**
 * @author Nicolas Morel
 */
public interface Context {

    /**
     * Creates the {@link Writer} to write the class.
     *
     * @param packageName the package
     * @param className the name of the class
     *
     * @return the {@link Writer} or null if the class already exists.
     */
    Optional<Writer> getWriter( String packageName, String className );

    /**
     * Writes the given type to the {@link Writer}.
     *
     * @param packageName the package for the type
     * @param type the type
     * @param writer the writer
     */
    void write( String packageName, TypeSpec type, Writer writer );

    void warn( String message );

    void error( String message );

    RuntimeException logAndThrow( String message );
}
