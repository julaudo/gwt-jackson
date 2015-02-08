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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.github.nmorel.gwtjackson.client.JsonDeserializer;
import com.github.nmorel.gwtjackson.client.JsonSerializer;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractBeanJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractDelegationBeanJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractObjectBeanJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractSerializableBeanJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.TypeDeserializationInfo;
import com.github.nmorel.gwtjackson.client.ser.bean.AbstractBeanJsonSerializer;
import com.github.nmorel.gwtjackson.client.ser.bean.AbstractValueBeanJsonSerializer;
import com.github.nmorel.gwtjackson.client.ser.bean.TypeSerializationInfo;
import com.github.nmorel.gwtjackson.rebind.bean.BeanInfo;
import com.github.nmorel.gwtjackson.rebind.bean.BeanProcessor;
import com.github.nmorel.gwtjackson.rebind.bean.BeanTypeInfo;
import com.github.nmorel.gwtjackson.rebind.exception.UnsupportedTypeException;
import com.github.nmorel.gwtjackson.rebind.property.PropertiesContainer;
import com.github.nmorel.gwtjackson.rebind.property.PropertyInfo;
import com.github.nmorel.gwtjackson.rebind.property.processor.PropertyProcessor;
import com.github.nmorel.gwtjackson.rebind.type.JMapperType;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JTypeParameter;
import com.google.gwt.thirdparty.guava.common.base.Optional;
import com.google.gwt.thirdparty.guava.common.base.Strings;
import com.google.gwt.thirdparty.guava.common.collect.ImmutableList;
import com.google.gwt.thirdparty.guava.common.collect.ImmutableMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import static com.github.nmorel.gwtjackson.rebind.CreatorUtils.isObject;
import static com.github.nmorel.gwtjackson.rebind.CreatorUtils.isSerializable;
import static com.github.nmorel.gwtjackson.rebind.writer.JTypeName.parameterizedName;
import static com.github.nmorel.gwtjackson.rebind.writer.JTypeName.rawName;
import static com.github.nmorel.gwtjackson.rebind.writer.JTypeName.typeVariableName;

/**
 * @author Nicolas Morel
 */
public abstract class AbstractBeanJsonCreator extends AbstractCreator {

    protected final BeanJsonMapperInfo mapperInfo;

    protected final BeanInfo beanInfo;

    protected final ImmutableMap<String, PropertyInfo> properties;

    protected TypeSpec.Builder typeBuilder;

    public AbstractBeanJsonCreator( TreeLogger logger, GeneratorContext context, RebindConfiguration configuration, JacksonTypeOracle
            typeOracle, JClassType beanType ) throws UnableToCompleteException {
        super( logger, context, configuration, typeOracle );
        this.mapperInfo = initMapperInfo( beanType );
        this.beanInfo = mapperInfo.getBeanInfo();
        this.properties = mapperInfo.getProperties();
    }

    private BeanJsonMapperInfo initMapperInfo( JClassType beanType ) throws UnableToCompleteException {
        BeanJsonMapperInfo mapperInfo = typeOracle.getBeanJsonMapperInfo( beanType );
        if ( null != mapperInfo ) {
            return mapperInfo;
        }

        boolean samePackage = true;
        String packageName = beanType.getPackage().getName();
        if ( packageName.startsWith( "java." ) ) {
            packageName = "gwtjackson." + packageName;
            samePackage = false;
        }

        // retrieve the informations on the beans and its properties
        BeanInfo beanInfo = BeanProcessor.processBean( logger, typeOracle, configuration, beanType );
        PropertiesContainer properties = PropertyProcessor
                .findAllProperties( configuration, logger, typeOracle, beanInfo, samePackage );
        beanInfo = BeanProcessor.processProperties( configuration, logger, typeOracle, beanInfo, properties );

        // we concatenate the name of all the enclosing class
        StringBuilder builder = new StringBuilder( beanType.getSimpleSourceName() );
        JClassType enclosingType = beanType.getEnclosingType();
        while ( null != enclosingType ) {
            builder.insert( 0, enclosingType.getSimpleSourceName() + "_" );
            enclosingType = enclosingType.getEnclosingType();
        }

        // if the type is specific to the mapper, we concatenate the name and hash of the mapper to it
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

    @Override
    protected Optional<BeanJsonMapperInfo> getMapperInfo() {
        return Optional.of( mapperInfo );
    }

    /**
     * Creates an implementation of {@link AbstractBeanJsonSerializer} for the type given in
     * parameter
     *
     * @return the information about the created class
     */
    public final BeanJsonMapperInfo create() throws UnableToCompleteException, UnsupportedTypeException {

        PrintWriter printWriter = getPrintWriter( mapperInfo.getPackageName(), isSerializer() ? mapperInfo
                .getSimpleSerializerClassName() : mapperInfo.getSimpleDeserializerClassName() );
        // the class already exists, no need to continue
        if ( printWriter == null ) {
            return mapperInfo;
        }

        try {

            TypeName superclass;
            if ( isSerializer() ) {
                Class clazz;
                if ( mapperInfo.getBeanInfo().getValuePropertyInfo().isPresent() ) {
                    clazz = AbstractValueBeanJsonSerializer.class;
                } else {
                    clazz = AbstractBeanJsonSerializer.class;
                }
                superclass = parameterizedName( clazz, mapperInfo.getType() );
            } else {
                if ( isObject( mapperInfo.getType() ) ) {
                    superclass = ClassName.get( AbstractObjectBeanJsonDeserializer.class );
                } else if ( isSerializable( mapperInfo.getType() ) ) {
                    superclass = ClassName.get( AbstractSerializableBeanJsonDeserializer.class );
                } else if ( mapperInfo.getBeanInfo().isCreatorDelegation() ) {
                    superclass = parameterizedName( AbstractDelegationBeanJsonDeserializer.class, mapperInfo.getType() );
                } else {
                    superclass = parameterizedName( AbstractBeanJsonDeserializer.class, mapperInfo.getType() );
                }
            }

            typeBuilder = TypeSpec.classBuilder( getSimpleClassName() )
                    .addModifiers( Modifier.PUBLIC )
                    .superclass( superclass );

            if ( null != mapperInfo.getType().isGenericType() ) {
                for ( JTypeParameter typeParameter : mapperInfo.getType().isGenericType().getTypeParameters() ) {
                    typeBuilder.addTypeVariable( typeVariableName( typeParameter ) );
                }
            }

            buildClass();

            write( mapperInfo.getPackageName(), typeBuilder.build(), printWriter );
        } finally {
            printWriter.close();
        }

        return mapperInfo;
    }

    protected abstract boolean isSerializer();

    protected String getSimpleClassName() {
        if ( isSerializer() ) {
            return mapperInfo.getSimpleSerializerClassName();
        } else {
            return mapperInfo.getSimpleDeserializerClassName();
        }
    }

    protected void buildClass() throws UnableToCompleteException, UnsupportedTypeException {
        typeBuilder.addMethod( buildConstructor() );
        typeBuilder.addMethod( buildClassGetterMethod() );
        buildSpecific();
    }

    private MethodSpec buildConstructor() {
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder().addModifiers( Modifier.PUBLIC );

        if ( !beanInfo.getParameterizedTypes().isEmpty() ) {
            Class mapperClass;
            String mapperNameFormat;
            if ( isSerializer() ) {
                mapperClass = JsonSerializer.class;
                mapperNameFormat = TYPE_PARAMETER_SERIALIZER_FIELD_NAME;
            } else {
                mapperClass = JsonDeserializer.class;
                mapperNameFormat = TYPE_PARAMETER_DESERIALIZER_FIELD_NAME;
            }

            for ( int i = 0; i < beanInfo.getParameterizedTypes().size(); i++ ) {
                JClassType argType = beanInfo.getParameterizedTypes().get( i );
                String mapperName = String.format( mapperNameFormat, i );
                TypeName mapperType = parameterizedName( mapperClass, argType );

                FieldSpec field = FieldSpec.builder( mapperType, mapperName, Modifier.PRIVATE, Modifier.FINAL ).build();
                typeBuilder.addField( field );

                ParameterSpec parameter = ParameterSpec.builder( mapperType, mapperName ).build();
                constructorBuilder.addParameter( parameter );
                constructorBuilder.addStatement( "this.$N = $N", field, parameter );
            }
        }

        return constructorBuilder.build();
    }

    private MethodSpec buildClassGetterMethod() {
        return MethodSpec.methodBuilder( isSerializer() ? "getSerializedType" : "getDeserializedType" )
                .addModifiers( Modifier.PUBLIC )
                .addAnnotation( Override.class )
                .returns( Class.class )
                .addStatement( "return $T.class", rawName( beanInfo.getType() ) )
                .build();
    }

    protected abstract void buildSpecific() throws UnableToCompleteException, UnsupportedTypeException;

    protected CodeBlock generateTypeInfo( BeanTypeInfo typeInfo, boolean serialization ) {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add( "new $T($T.$L, $S)", serialization ? TypeSerializationInfo.class : TypeDeserializationInfo.class,
                        As.class, typeInfo.getInclude(), typeInfo.getPropertyName() )
                .indent()
                .indent();

        ImmutableMap<JClassType, String> mapTypeToMetadata;
        if ( serialization ) {
            mapTypeToMetadata = typeInfo.getMapTypeToSerializationMetadata();
        } else {
            mapTypeToMetadata = typeInfo.getMapTypeToDeserializationMetadata();
        }

        for ( Entry<JClassType, String> entry : mapTypeToMetadata.entrySet() ) {
            builder.add( "\n.addTypeInfo($T.class, $S)", rawName( entry.getKey() ), entry.getValue() );
        }

        return builder.unindent().unindent().build();
    }

    protected JClassType findFirstTypeToApplyPropertyAnnotation( JMapperType mapperType ) {
        return findFirstTypeToApplyPropertyAnnotation( Arrays.asList( mapperType ) );
    }

    private JClassType findFirstTypeToApplyPropertyAnnotation( List<JMapperType> mapperTypeList ) {
        if ( mapperTypeList.isEmpty() ) {
            return null;
        }

        List<JMapperType> subLevel = new ArrayList<JMapperType>();
        for ( JMapperType mapperType : mapperTypeList ) {
            if ( mapperType.isBeanMapper() ) {
                return mapperType.getType().isClass();
            } else if ( mapperType.getParameters().size() > 0 ) {
                subLevel.addAll( mapperType.getParameters() );
            }
        }

        return findFirstTypeToApplyPropertyAnnotation( subLevel );
    }

    protected void buildCommonPropertyParameters( CodeBlock.Builder paramBuilder, PropertyInfo property ) {
        if ( property.getFormat().isPresent() ) {
            JsonFormat format = property.getFormat().get();

            if ( !Strings.isNullOrEmpty( format.pattern() ) ) {
                paramBuilder.add( "\n.setPattern($S)", format.pattern() );
            }

            paramBuilder.add( "\n.setShape($T.$L)", Shape.class, format.shape().name() );

            if ( !Strings.isNullOrEmpty( format.locale() ) && !JsonFormat.DEFAULT_LOCALE.equals( format.locale() ) ) {
                logger.log( Type.WARN, "JsonFormat.locale is not supported by default" );
                paramBuilder.add( "\n.setLocale($S)", format.locale() );
            }
        }

        if ( property.getIgnoredProperties().isPresent() ) {
            for ( String ignoredProperty : property.getIgnoredProperties().get() ) {
                paramBuilder.add( "\n.addIgnoredProperty($S)", ignoredProperty );
            }
        }
    }

    protected ImmutableList<JClassType> filterSubtypes( BeanInfo beanInfo ) {
        if ( isSerializer() ) {
            return CreatorUtils.filterSubtypesForSerialization( logger, configuration, beanInfo.getType() );
        } else {
            return CreatorUtils.filterSubtypesForDeserialization( logger, configuration, beanInfo.getType() );
        }
    }
}
