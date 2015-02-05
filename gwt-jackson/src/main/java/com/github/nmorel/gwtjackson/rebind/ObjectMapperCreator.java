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
import java.io.PrintWriter;

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
import com.github.nmorel.gwtjackson.rebind.writer.JClassName;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JParameterizedType;
import com.google.gwt.thirdparty.guava.common.base.Optional;
import com.google.gwt.thirdparty.guava.common.base.Strings;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import static com.github.nmorel.gwtjackson.rebind.CreatorUtils.findFirstEncounteredAnnotationsOnAllHierarchy;

/**
 * @author Nicolas Morel
 */
public class ObjectMapperCreator extends AbstractCreator {

    private static final String OBJECT_MAPPER_CLASS = "com.github.nmorel.gwtjackson.client.ObjectMapper";

    private static final String OBJECT_READER_CLASS = "com.github.nmorel.gwtjackson.client.ObjectReader";

    private static final String OBJECT_WRITER_CLASS = "com.github.nmorel.gwtjackson.client.ObjectWriter";

    public ObjectMapperCreator( TreeLogger logger, GeneratorContext context, RebindConfiguration configuration, JacksonTypeOracle
            typeOracle ) throws UnableToCompleteException {
        super( logger, context, configuration, typeOracle );
    }

    @Override
    protected Optional<BeanJsonMapperInfo> getMapperInfo() {
        return Optional.absent();
    }

    /**
     * Creates the implementation of the interface denoted by typeName and extending {@link ObjectMapper}
     *
     * @param interfaceClass the interface to generate an implementation
     *
     * @return the fully qualified name of the created class
     * @throws UnableToCompleteException
     */
    public String create( JClassType interfaceClass ) throws UnableToCompleteException {
        // we concatenate the name of all the enclosing class
        StringBuilder builder = new StringBuilder( interfaceClass.getSimpleSourceName() + "Impl" );
        JClassType enclosingType = interfaceClass.getEnclosingType();
        while ( null != enclosingType ) {
            builder.insert( 0, enclosingType.getSimpleSourceName() + "_" );
            enclosingType = enclosingType.getEnclosingType();
        }

        String mapperClassSimpleName = builder.toString();
        String packageName = interfaceClass.getPackage()
                .getName();
        String qualifiedMapperClassName = packageName + "." + mapperClassSimpleName;

        PrintWriter printWriter = getPrintWriter( packageName, mapperClassSimpleName );
        // the class already exists, no need to continue
        if ( printWriter == null ) {
            return qualifiedMapperClassName;
        }

        // Extract the type of the object to map
        JClassType mappedTypeClass = getMappedType( interfaceClass );

        boolean reader = typeOracle.isObjectReader( interfaceClass );
        boolean writer = typeOracle.isObjectWriter( interfaceClass );
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
                .addSuperinterface( JClassName.get( interfaceClass ) )
                .superclass( ParameterizedTypeName.get( ClassName.get( abstractClass ), JClassName.get( mappedTypeClass ) ) )
                .addMethod( createConstructor( mappedTypeClass ) );

        if ( reader ) {
            mapperBuilder.addMethod( createDeserializer( mappedTypeClass ) );
        }

        if ( writer ) {
            mapperBuilder.addMethod( createSerializer( mappedTypeClass ) );
        }

        try {
            JavaFile.builder( packageName, mapperBuilder.build() )
                    .skipJavaLangImports( true )
                    .build()
                    .writeTo( printWriter );
            context.commit( logger, printWriter );
        } catch ( IOException e ) {
            logger.log( TreeLogger.Type.ERROR, "Error writing the file " + qualifiedMapperClassName, e );
            throw new UnableToCompleteException();
        }

        return qualifiedMapperClassName;
    }

    private JClassType getMappedType( JClassType interfaceClass ) throws UnableToCompleteException {
        JClassType intf = interfaceClass.isInterface();
        if ( intf == null ) {
            logger.log( TreeLogger.Type.ERROR, "Expected " + interfaceClass + " to be an interface." );
            throw new UnableToCompleteException();
        }

        JClassType[] intfs = intf.getImplementedInterfaces();
        for ( JClassType t : intfs ) {
            if ( t.getQualifiedSourceName()
                    .equals( OBJECT_MAPPER_CLASS ) ) {
                return extractParameterizedType( OBJECT_MAPPER_CLASS, t.isParameterized() );
            } else if ( t.getQualifiedSourceName()
                    .equals( OBJECT_READER_CLASS ) ) {
                return extractParameterizedType( OBJECT_READER_CLASS, t.isParameterized() );
            } else if ( t.getQualifiedSourceName()
                    .equals( OBJECT_WRITER_CLASS ) ) {
                return extractParameterizedType( OBJECT_WRITER_CLASS, t.isParameterized() );
            }
        }
        logger.log( TreeLogger.Type.ERROR, "Expected  " + interfaceClass + " to extend one of the interface " + OBJECT_MAPPER_CLASS + ", " +
                OBJECT_READER_CLASS + " or " + OBJECT_WRITER_CLASS );
        throw new UnableToCompleteException();
    }

    private JClassType extractParameterizedType( String clazz, JParameterizedType genericType ) throws UnableToCompleteException {
        if ( genericType == null ) {
            logger.log( TreeLogger.Type.ERROR, "Expected the " + clazz + " declaration to specify a " +
                    "parameterized type." );
            throw new UnableToCompleteException();
        }
        JClassType[] typeParameters = genericType.getTypeArgs();
        if ( typeParameters == null || typeParameters.length != 1 ) {
            logger.log( TreeLogger.Type.ERROR, "Expected the " + clazz + " declaration to specify 1 " +
                    "parameterized type." );
            throw new UnableToCompleteException();
        }
        return typeParameters[0];
    }

    private MethodSpec createConstructor( JClassType mappedTypeClass ) {
        Optional<JsonRootName> jsonRootName = findFirstEncounteredAnnotationsOnAllHierarchy( configuration, mappedTypeClass, JsonRootName
                .class );
        String rootName;
        if ( !jsonRootName.isPresent() || Strings.isNullOrEmpty( jsonRootName.get()
                .value() ) ) {
            rootName = mappedTypeClass.getSimpleSourceName();
        } else {
            rootName = jsonRootName.get()
                    .value();
        }

        return MethodSpec.constructorBuilder()
                .addModifiers( Modifier.PUBLIC )
                .addStatement( "super($S)", rootName )
                .build();
    }

    private MethodSpec createDeserializer( JClassType mappedTypeClass ) throws UnableToCompleteException {
        JDeserializerType type;
        try {
            type = getJsonDeserializerFromType( mappedTypeClass );
        } catch ( UnsupportedTypeException e ) {
            logger.log( Type.ERROR, "Cannot generate mapper due to previous errors : " + e.getMessage() );
            throw new UnableToCompleteException();
        }

        return MethodSpec.methodBuilder( "newDeserializer" )
                .addModifiers( Modifier.PROTECTED )
                .addAnnotation( Override.class )
                .returns( ParameterizedTypeName.get( ClassName.get( JsonDeserializer.class ), JClassName.get( mappedTypeClass ) ) )
                .addStatement( "return $L", type.getInstance() )
                .build();
    }

    private MethodSpec createSerializer( JClassType mappedTypeClass ) throws UnableToCompleteException {
        JSerializerType type;
        try {
            type = getJsonSerializerFromType( mappedTypeClass );
        } catch ( UnsupportedTypeException e ) {
            logger.log( Type.ERROR, "Cannot generate mapper due to previous errors : " + e.getMessage() );
            throw new UnableToCompleteException();
        }

        return MethodSpec.methodBuilder( "newSerializer" )
                .addModifiers( Modifier.PROTECTED )
                .addAnnotation( Override.class )
                .returns( ParameterizedTypeName.get( ClassName.get( JsonSerializer.class ), JClassName.get( mappedTypeClass ) ) )
                .addStatement( "return $L", type.getInstance() )
                .build();
    }
}
