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

package com.github.nmorel.gwtjackson.rebind;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.Writer;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.github.nmorel.gwtjackson.client.AbstractObjectMapper;
import com.github.nmorel.gwtjackson.client.AbstractObjectReader;
import com.github.nmorel.gwtjackson.client.AbstractObjectWriter;
import com.github.nmorel.gwtjackson.client.JsonDeserializer;
import com.github.nmorel.gwtjackson.client.JsonSerializer;
import com.github.nmorel.gwtjackson.client.ObjectMapper;
import com.github.nmorel.gwtjackson.rebind.exception.UnsupportedTypeException;
import com.github.nmorel.gwtjackson.rebind.type.JDeserializerType;
import com.github.nmorel.gwtjackson.rebind.type.JSerializerType;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JParameterizedType;
import com.google.gwt.thirdparty.guava.common.base.Optional;
import com.google.gwt.thirdparty.guava.common.base.Strings;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import static com.github.nmorel.gwtjackson.rebind.CreatorUtils.findFirstEncounteredAnnotationsOnAllHierarchy;
import static com.github.nmorel.gwtjackson.rebind.writer.JTypeName.parameterizedName;
import static com.github.nmorel.gwtjackson.rebind.writer.JTypeName.typeName;

/**
 * @author Nicolas Morel
 */
public class ObjectMapperCreator extends AbstractCreator {

    private static final String OBJECT_MAPPER_CLASS = "com.github.nmorel.gwtjackson.client.ObjectMapper";

    private static final String OBJECT_READER_CLASS = "com.github.nmorel.gwtjackson.client.ObjectReader";

    private static final String OBJECT_WRITER_CLASS = "com.github.nmorel.gwtjackson.client.ObjectWriter";

    public ObjectMapperCreator( GwtContext context ) {
        super( context );
    }

    @Override
    protected Optional<BeanJsonMapperInfo> getMapperInfo() {
        return Optional.absent();
    }

    /**
     * Creates the implementation of the interface denoted by interfaceClass and extending {@link ObjectMapper}
     *
     * @param interfaceClass the interface to generate an implementation
     *
     * @return the fully qualified name of the created class
     */
    public String create( JClassType interfaceClass ) {
        // We concatenate the name of all the enclosing class.
        StringBuilder builder = new StringBuilder( interfaceClass.getSimpleSourceName() + "Impl" );
        JClassType enclosingType = interfaceClass.getEnclosingType();
        while ( null != enclosingType ) {
            builder.insert( 0, enclosingType.getSimpleSourceName() + "_" );
            enclosingType = enclosingType.getEnclosingType();
        }

        String mapperClassSimpleName = builder.toString();
        String packageName = interfaceClass.getPackage().getName();
        String qualifiedMapperClassName = packageName + "." + mapperClassSimpleName;

        Optional<Writer> printWriter = context.getWriter( packageName, mapperClassSimpleName );
        // The class already exists, no need to continue.
        if ( !printWriter.isPresent() ) {
            return qualifiedMapperClassName;
        }

        try {
            // Extract the type of the object to map.
            JClassType mappedTypeClass = extractMappedType( interfaceClass );

            boolean reader = context.isObjectReader( interfaceClass );
            boolean writer = context.isObjectWriter( interfaceClass );
            Class<?> abstractClass;
            if ( reader ) {
                if ( writer ) {
                    abstractClass = AbstractObjectMapper.class;
                } else {
                    abstractClass = AbstractObjectReader.class;
                }
            } else {
                abstractClass = AbstractObjectWriter.class;
            }

            TypeSpec.Builder mapperBuilder = TypeSpec.classBuilder( mapperClassSimpleName )
                    .addModifiers( Modifier.PUBLIC, Modifier.FINAL )
                    .addSuperinterface( typeName( interfaceClass ) )
                    .superclass( parameterizedName( abstractClass, mappedTypeClass ) )
                    .addMethod( buildConstructor( mappedTypeClass ) );

            if ( reader ) {
                mapperBuilder.addMethod( buildNewDeserializerMethod( mappedTypeClass ) );
            }

            if ( writer ) {
                mapperBuilder.addMethod( buildNewSerializerMethod( mappedTypeClass ) );
            }

            context.write( packageName, mapperBuilder.build(), printWriter.get() );
        } finally {
            try {
                printWriter.get().close();
            } catch ( IOException e ) {
                // nothing
            }
        }

        return qualifiedMapperClassName;
    }

    /**
     * Extract the type to map from the interface.
     *
     * @param interfaceClass the interface
     *
     * @return the extracted type to map
     */
    private JClassType extractMappedType( JClassType interfaceClass ) {
        JClassType intf = interfaceClass.isInterface();
        if ( intf == null ) {
            throw context.logAndThrow( "Expected " + interfaceClass + " to be an interface." );
        }

        JClassType[] intfs = intf.getImplementedInterfaces();
        for ( JClassType t : intfs ) {
            if ( t.getQualifiedSourceName().equals( OBJECT_MAPPER_CLASS ) ) {
                return extractParameterizedType( OBJECT_MAPPER_CLASS, t.isParameterized() );
            } else if ( t.getQualifiedSourceName().equals( OBJECT_READER_CLASS ) ) {
                return extractParameterizedType( OBJECT_READER_CLASS, t.isParameterized() );
            } else if ( t.getQualifiedSourceName().equals( OBJECT_WRITER_CLASS ) ) {
                return extractParameterizedType( OBJECT_WRITER_CLASS, t.isParameterized() );
            }
        }
        throw context.logAndThrow( "Expected  " + interfaceClass + " to extend one of the following interface : " +
                OBJECT_MAPPER_CLASS + ", " + OBJECT_READER_CLASS + " or " + OBJECT_WRITER_CLASS );
    }

    /**
     * Extract the parameter's type.
     *
     * @param clazz the name of the interface
     * @param parameterizedType the parameterized type
     *
     * @return the extracted type
     */
    private JClassType extractParameterizedType( String clazz, JParameterizedType parameterizedType ) {
        if ( parameterizedType == null ) {
            throw context.logAndThrow( "Expected the " + clazz + " declaration to specify a parameterized type." );
        }
        JClassType[] typeParameters = parameterizedType.getTypeArgs();
        if ( typeParameters == null || typeParameters.length != 1 ) {
            throw context.logAndThrow( "Expected the " + clazz + " declaration to specify 1 parameterized type." );
        }
        return typeParameters[0];
    }

    /**
     * Build the constructor.
     *
     * @param mappedTypeClass the type to map
     *
     * @return the constructor method
     */
    private MethodSpec buildConstructor( JClassType mappedTypeClass ) {
        Optional<JsonRootName> jsonRootName
                = findFirstEncounteredAnnotationsOnAllHierarchy( context, mappedTypeClass, JsonRootName.class );
        String rootName;
        if ( !jsonRootName.isPresent() || Strings.isNullOrEmpty( jsonRootName.get().value() ) ) {
            rootName = mappedTypeClass.getSimpleSourceName();
        } else {
            rootName = jsonRootName.get().value();
        }

        return MethodSpec.constructorBuilder()
                .addModifiers( Modifier.PUBLIC )
                .addStatement( "super($S)", rootName )
                .build();
    }

    /**
     * Build the new deserializer method.
     *
     * @param mappedTypeClass the type to map
     *
     * @return the method
     */
    private MethodSpec buildNewDeserializerMethod( JClassType mappedTypeClass ) {
        JDeserializerType type;
        try {
            type = getJsonDeserializerFromType( mappedTypeClass );
        } catch ( UnsupportedTypeException e ) {
            throw context.logAndThrow( "Cannot generate mapper due to previous errors : " + e.getMessage() );
        }

        return MethodSpec.methodBuilder( "newDeserializer" )
                .addModifiers( Modifier.PROTECTED )
                .addAnnotation( Override.class )
                .returns( parameterizedName( JsonDeserializer.class, mappedTypeClass ) )
                .addStatement( "return $L", type.getInstance() )
                .build();
    }

    /**
     * Build the new serializer method.
     *
     * @param mappedTypeClass the type to map
     *
     * @return the method
     */
    private MethodSpec buildNewSerializerMethod( JClassType mappedTypeClass ) {
        JSerializerType type;
        try {
            type = getJsonSerializerFromType( mappedTypeClass );
        } catch ( UnsupportedTypeException e ) {
            throw context.logAndThrow( "Cannot generate mapper due to previous errors : " + e.getMessage() );
        }

        return MethodSpec.methodBuilder( "newSerializer" )
                .addModifiers( Modifier.PROTECTED )
                .addAnnotation( Override.class )
                .returns( parameterizedName( JsonSerializer.class, mappedTypeClass ) )
                .addStatement( "return $L", type.getInstance() )
                .build();
    }
}
