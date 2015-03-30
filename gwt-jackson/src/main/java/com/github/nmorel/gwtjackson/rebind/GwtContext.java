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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import com.github.nmorel.gwtjackson.rebind.RebindConfiguration.MapperInstance;
import com.github.nmorel.gwtjackson.rebind.bean.BeanInfo;
import com.github.nmorel.gwtjackson.rebind.bean.BeanProcessor;
import com.github.nmorel.gwtjackson.rebind.exception.UnexpectedErrorException;
import com.github.nmorel.gwtjackson.rebind.property.PropertiesContainer;
import com.github.nmorel.gwtjackson.rebind.property.PropertyProcessor;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.thirdparty.guava.common.base.Optional;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

/**
 * @author Nicolas Morel
 */
public class GwtContext implements Context {

    private final TreeLogger logger;

    private final GeneratorContext context;

    private final RebindConfiguration configuration;

    private final JacksonTypeOracle typeOracle;

    public GwtContext( TreeLogger logger, GeneratorContext context, RebindConfiguration configuration, JacksonTypeOracle typeOracle ) {
        this.logger = logger;
        this.context = context;
        this.configuration = configuration;
        this.typeOracle = typeOracle;
    }

    @Override
    public Optional<Writer> getWriter( String packageName, String className ) {
        Writer writer = context.tryCreate( logger, packageName, className );
        return Optional.fromNullable( writer );
    }

    @Override
    public void write( String packageName, TypeSpec type, Writer writer ) {
        try {
            JavaFile.builder( packageName, type )
                    .build()
                    .writeTo( writer );
            context.commit( logger, (PrintWriter) writer );
        } catch ( IOException e ) {
            String message = "Error writing the file " + packageName + "." + type.name;
            logger.log( TreeLogger.Type.ERROR, message, e );
            throw new UnexpectedErrorException( message, e );
        }
    }

    /**
     * Returns the mapper information for the given type. The result is cached.
     *
     * @param type the type
     *
     * @return the mapper information
     */
    public final BeanJsonMapperInfo getMapperInfo( JType type ) {
        JClassType beanType = type.isClassOrInterface();

        BeanJsonMapperInfo mapperInfo = typeOracle.getBeanJsonMapperInfo( beanType );
        if ( null != mapperInfo ) {
            return mapperInfo;
        }

        boolean samePackage = true;
        String packageName = beanType.getPackage().getName();
        // We can't create classes in the java package so we prefix it.
        if ( packageName.startsWith( "java." ) ) {
            packageName = "gwtjackson." + packageName;
            samePackage = false;
        }

        // Retrieve the informations on the beans and its properties.
        BeanInfo beanInfo = BeanProcessor.processBean( logger, typeOracle, configuration, beanType );
        PropertiesContainer properties = PropertyProcessor
                .findAllProperties( configuration, logger, typeOracle, beanInfo, samePackage );
        beanInfo = BeanProcessor.processProperties( configuration, logger, typeOracle, beanInfo, properties );

        // We concatenate the name of all the enclosing classes.
        StringBuilder builder = new StringBuilder( beanType.getSimpleSourceName() );
        JClassType enclosingType = beanType.getEnclosingType();
        while ( null != enclosingType ) {
            builder.insert( 0, enclosingType.getSimpleSourceName() + "_" );
            enclosingType = enclosingType.getEnclosingType();
        }

        // If the type is specific to the mapper, we concatenate the name and hash of the mapper to it.
        boolean isSpecificToMapper = configuration.isSpecificToMapper( beanType );
        if ( isSpecificToMapper ) {
            JClassType rootMapperClass = configuration.getRootMapperClass();
            builder.insert( 0, '_' ).insert( 0, configuration.getRootMapperHash() ).insert( 0, '_' ).insert( 0, rootMapperClass
                    .getSimpleSourceName() );
        }

        String simpleSerializerClassName = builder.toString() + "BeanJsonSerializerImpl";
        String simpleDeserializerClassName = builder.toString() + "BeanJsonDeserializerImpl";

        mapperInfo = new BeanJsonMapperInfo( beanType, packageName, samePackage, simpleSerializerClassName,
                simpleDeserializerClassName, beanInfo, properties
                .getProperties() );

        typeOracle.addBeanJsonMapperInfo( beanType, mapperInfo );

        return mapperInfo;
    }

    /**
     * Return a {@link MapperInstance} instantiating the serializer for the given type
     */
    public Optional<MapperInstance> getSerializer( JType type ) {
        return configuration.getSerializer( type );
    }

    /**
     * Return a {@link MapperInstance} instantiating the deserializer for the given type
     */
    public Optional<MapperInstance> getDeserializer( JType type ) {
        return configuration.getDeserializer( type );
    }

    /**
     * Return a {@link MapperInstance} instantiating the key serializer for the given type
     */
    public Optional<MapperInstance> getKeySerializer( JType type ) {
        return configuration.getKeySerializer( type );
    }

    /**
     * Return a {@link MapperInstance} instantiating the key deserializer for the given type
     */
    public Optional<MapperInstance> getKeyDeserializer( JType type ) {
        return configuration.getKeyDeserializer( type );
    }

    /**
     * Return the mixin type for the given type
     */
    public Optional<JClassType> getMixInAnnotations( JType type ) {
        return configuration.getMixInAnnotations( type );
    }

    public boolean isObjectReader( JClassType type ) {
        return typeOracle.isObjectReader( type );
    }

    public boolean isObjectWriter( JClassType type ) {
        return typeOracle.isObjectWriter( type );
    }

    public boolean isJavaScriptObject( JType type ) {
        return typeOracle.isJavaScriptObject( type );
    }

    public JClassType getJavaScriptObject() {
        return typeOracle.getJavaScriptObject();
    }

    @Override
    public void warn( String message ) {
        logger.log( TreeLogger.Type.WARN, message );
    }

    @Override
    public void error( String message ) {
        logger.log( TreeLogger.Type.ERROR, message );
    }

    @Override
    public RuntimeException logAndThrow( String message ) {
        logger.log( TreeLogger.Type.ERROR, message );
        return new UnexpectedErrorException( message );
    }

    public GwtContext branch( String message ) {
        return new GwtContext( logger.branch( Type.DEBUG, message ),
                context, configuration, typeOracle );
    }
}
