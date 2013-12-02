/*
 * Copyright 2013 Nicolas Morel
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

package com.github.nmorel.gwtjackson.client;

import java.io.IOException;

import com.github.nmorel.gwtjackson.client.exception.JsonDeserializationException;
import com.github.nmorel.gwtjackson.client.stream.JsonReader;
import com.github.nmorel.gwtjackson.client.stream.JsonToken;

/**
 * Base class for all the deserializer. It handles null values and exceptions. The rest is delegated to implementations.
 *
 * @author Nicolas Morel
 */
public abstract class JsonDeserializer<T> {

    /**
     * Deserializes a JSON input into an object.
     *
     * @param reader {@link JsonReader} used to read the JSON input
     * @param ctx Context for the full deserialization process
     *
     * @return the deserialized object
     * @throws JsonDeserializationException if an error occurs during the deserialization
     */
    public T deserialize( JsonReader reader, JsonDeserializationContext ctx ) throws JsonDeserializationException {
        try {
            if ( JsonToken.NULL.equals( reader.peek() ) ) {
                reader.skipValue();
                return null;
            }
            return doDeserialize( reader, ctx );
        } catch ( IOException e ) {
            throw ctx.traceError( e, reader );
        } catch ( JsonDeserializationException e ) {
            // already logged, we just throw it
            throw e;
        } catch ( Exception e ) {
            throw ctx.traceError( e, reader );
        }
    }

    /**
     * Deserializes a non-null JSON input into an object.
     *
     * @param reader {@link JsonReader} used to read the JSON input
     * @param ctx Context for the full deserialization process
     *
     * @return the deserialized object
     * @throws IOException if an error occurs reading the input
     */
    protected abstract T doDeserialize( JsonReader reader, JsonDeserializationContext ctx ) throws IOException;

    /**
     * Set the back reference.
     *
     * @param referenceName name of the reference
     * @param reference reference to set
     * @param value value to set the reference to.
     * @param ctx Context for the full deserialization process
     *
     * @see com.fasterxml.jackson.annotation.JsonBackReference
     */
    public void setBackReference( String referenceName, Object reference, T value, JsonDeserializationContext ctx ) {
        throw new JsonDeserializationException( "Cannot set a back reference to the type managed by this deserializer" );
    }
}