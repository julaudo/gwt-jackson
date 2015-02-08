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
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.github.nmorel.gwtjackson.client.JsonDeserializer;
import com.github.nmorel.gwtjackson.client.JsonSerializationContext;
import com.github.nmorel.gwtjackson.client.JsonSerializer;
import com.github.nmorel.gwtjackson.client.JsonSerializerParameters;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractBeanJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractDelegationBeanJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractIdentityDeserializationInfo;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractObjectBeanJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractSerializableBeanJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.PropertyIdentityDeserializationInfo;
import com.github.nmorel.gwtjackson.client.deser.bean.TypeDeserializationInfo;
import com.github.nmorel.gwtjackson.client.ser.bean.AbstractBeanJsonSerializer;
import com.github.nmorel.gwtjackson.client.ser.bean.AbstractIdentitySerializationInfo;
import com.github.nmorel.gwtjackson.client.ser.bean.AbstractValueBeanJsonSerializer;
import com.github.nmorel.gwtjackson.client.ser.bean.ObjectIdSerializer;
import com.github.nmorel.gwtjackson.client.ser.bean.PropertyIdentitySerializationInfo;
import com.github.nmorel.gwtjackson.client.ser.bean.TypeSerializationInfo;
import com.github.nmorel.gwtjackson.client.ser.map.MapJsonSerializer;
import com.github.nmorel.gwtjackson.rebind.bean.BeanIdentityInfo;
import com.github.nmorel.gwtjackson.rebind.bean.BeanInfo;
import com.github.nmorel.gwtjackson.rebind.bean.BeanProcessor;
import com.github.nmorel.gwtjackson.rebind.bean.BeanTypeInfo;
import com.github.nmorel.gwtjackson.rebind.exception.UnsupportedTypeException;
import com.github.nmorel.gwtjackson.rebind.property.FieldAccessor.Accessor;
import com.github.nmorel.gwtjackson.rebind.property.PropertiesContainer;
import com.github.nmorel.gwtjackson.rebind.property.PropertyInfo;
import com.github.nmorel.gwtjackson.rebind.property.processor.PropertyProcessor;
import com.github.nmorel.gwtjackson.rebind.type.JDeserializerType;
import com.github.nmorel.gwtjackson.rebind.type.JMapperType;
import com.github.nmorel.gwtjackson.rebind.type.JSerializerType;
import com.github.nmorel.gwtjackson.rebind.writer.JClassName;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.core.ext.typeinfo.JTypeParameter;
import com.google.gwt.i18n.client.TimeZone;
import com.google.gwt.thirdparty.guava.common.base.Optional;
import com.google.gwt.thirdparty.guava.common.base.Strings;
import com.google.gwt.thirdparty.guava.common.collect.ImmutableList;
import com.google.gwt.thirdparty.guava.common.collect.ImmutableMap;
import com.google.gwt.user.rebind.SourceWriter;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import static com.github.nmorel.gwtjackson.rebind.CreatorUtils.isObject;
import static com.github.nmorel.gwtjackson.rebind.CreatorUtils.isSerializable;

/**
 * @author Nicolas Morel
 */
public abstract class AbstractBeanJsonCreator extends AbstractCreator {

    protected static final String ABSTRACT_BEAN_JSON_DESERIALIZER_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean" + "" +
            ".AbstractBeanJsonDeserializer";

    protected static final String TYPE_DESERIALIZATION_INFO_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean" + "" +
            ".TypeDeserializationInfo";

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
                superclass = ParameterizedTypeName.get( ClassName.get( clazz ), JClassName.get( mapperInfo.getType() ) );
            } else {
                if ( isObject( mapperInfo.getType() ) ) {
                    superclass = ClassName.get( AbstractObjectBeanJsonDeserializer.class );
                } else if ( isSerializable( mapperInfo.getType() ) ) {
                    superclass = ClassName.get( AbstractSerializableBeanJsonDeserializer.class );
                } else if ( mapperInfo.getBeanInfo().isCreatorDelegation() ) {
                    superclass = ParameterizedTypeName.get( ClassName.get( AbstractDelegationBeanJsonDeserializer.class ), JClassName
                            .get( mapperInfo.getType() ) );
                } else {
                    superclass = ParameterizedTypeName.get( ClassName.get( AbstractBeanJsonDeserializer.class ), JClassName.get( mapperInfo
                            .getType() ) );
                }
            }

            typeBuilder = TypeSpec.classBuilder( getSimpleClassName() )
                    .addModifiers( Modifier.PUBLIC )
                    .superclass( superclass );

            if ( null != mapperInfo.getType().isGenericType() ) {
                for ( JTypeParameter typeParameter : mapperInfo.getType().isGenericType().getTypeParameters() ) {
                    typeBuilder.addTypeVariable( JClassName.getTypeVariableName( typeParameter ) );
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
            ClassName mapperClass;
            String mapperNameFormat;
            if ( isSerializer() ) {
                mapperClass = ClassName.get( JsonSerializer.class );
                mapperNameFormat = TYPE_PARAMETER_SERIALIZER_FIELD_NAME;
            } else {
                mapperClass = ClassName.get( JsonDeserializer.class );
                mapperNameFormat = TYPE_PARAMETER_DESERIALIZER_FIELD_NAME;
            }

            for ( int i = 0; i < beanInfo.getParameterizedTypes().size(); i++ ) {
                JClassType argType = beanInfo.getParameterizedTypes().get( i );
                String mapperName = String.format( mapperNameFormat, i );
                TypeName mapperType = ParameterizedTypeName.get( mapperClass, JClassName.get( argType ) );

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
                .addStatement( "return $T.class", JClassName.getRaw( beanInfo.getType() ) )
                .build();
    }

    protected abstract void buildSpecific() throws UnableToCompleteException, UnsupportedTypeException;

    protected String getParameterizedQualifiedClassName( JType type ) {
        if ( null == type.isPrimitive() ) {
            return type.getParameterizedQualifiedSourceName();
        } else {
            return type.isPrimitive().getQualifiedBoxedSourceName();
        }
    }

    protected Optional<JSerializerType> getIdentitySerializerType( BeanIdentityInfo identityInfo ) throws UnableToCompleteException,
            UnsupportedTypeException {
        if ( identityInfo.isIdABeanProperty() ) {
            return Optional.absent();
        } else {
            return Optional.of( getJsonSerializerFromType( identityInfo.getType().get() ) );
        }
    }

    protected TypeSpec generateIdentifierSerializationInfo( JClassType type, BeanIdentityInfo identityInfo,
                                                            Optional<JSerializerType> serializerType ) throws UnableToCompleteException,
            UnsupportedTypeException {

        TypeSpec.Builder builder = TypeSpec
                .anonymousClassBuilder( "$L, $S", identityInfo.isAlwaysAsId(), identityInfo.getPropertyName() );

        if ( identityInfo.isIdABeanProperty() ) {

            BeanJsonMapperInfo mapperInfo = typeOracle.getBeanJsonMapperInfo( type );
            PropertyInfo propertyInfo = mapperInfo.getProperties().get( identityInfo.getPropertyName() );
            JSerializerType propertySerializerType = getJsonSerializerFromType( propertyInfo.getType() );

            builder.superclass( JClassName.get( PropertyIdentitySerializationInfo.class, type, propertyInfo.getType() ) );

            buildBeanPropertySerializerBody( builder, type, propertyInfo, propertySerializerType );

        } else {
            JType qualifiedType = identityInfo.getType().get();

            builder.superclass( JClassName.get( AbstractIdentitySerializationInfo.class, type, qualifiedType ) );

            builder.addMethod( MethodSpec.methodBuilder( "newSerializer" )
                    .addModifiers( Modifier.PROTECTED )
                    .addAnnotation( Override.class )
                    .returns( ParameterizedTypeName.get( ClassName.get( JsonSerializer.class ), WildcardTypeName
                            .subtypeOf( Object.class ) ) )
                    .addStatement( "return $L", serializerType.get().getInstance() )
                    .build() );

            TypeName generatorType = JClassName.get( ObjectIdGenerator.class, qualifiedType );
            TypeName returnType = JClassName.get( ObjectIdSerializer.class, qualifiedType );

            builder.addMethod( MethodSpec.methodBuilder( "getObjectId" )
                    .addModifiers( Modifier.PUBLIC )
                    .addAnnotation( Override.class )
                    .returns( returnType )
                    .addParameter( JClassName.get( type ), "bean" )
                    .addParameter( JsonSerializationContext.class, "ctx" )
                    .addStatement( "$T generator = new $T().forScope($T.class)",
                            generatorType, identityInfo.getGenerator(), identityInfo.getScope() )
                    .addStatement( "$T scopedGen = ctx.findObjectIdGenerator(generator)", generatorType )
                    .beginControlFlow( "if (null == scopedGen)" )
                    .addStatement( "scopedGen = generator.newForSerialization(ctx)" )
                    .addStatement( "ctx.addGenerator(scopedGen)" )
                    .endControlFlow()
                    .addStatement( "return new $T(scopedGen.generateId(bean), getSerializer())", returnType )
                    .build() );
        }

        return builder.build();
    }

    protected Optional<JDeserializerType> getIdentityDeserializerType( BeanIdentityInfo identityInfo ) throws UnableToCompleteException,
            UnsupportedTypeException {
        if ( identityInfo.isIdABeanProperty() ) {
            return Optional.absent();
        } else {
            return Optional.of( getJsonDeserializerFromType( identityInfo.getType().get() ) );
        }
    }

    protected void generateIdentifierDeserializationInfo( SourceWriter source, JClassType type, BeanIdentityInfo identityInfo,
                                                          Optional<JDeserializerType> deserializerType ) throws UnableToCompleteException {
        if ( identityInfo.isIdABeanProperty() ) {

            source.print( "new %s<%s>(\"%s\", %s.class, %s.class)", PropertyIdentityDeserializationInfo.class.getName(), type
                    .getParameterizedQualifiedSourceName(), identityInfo.getPropertyName(), identityInfo.getGenerator()
                    .getCanonicalName(), identityInfo.getScope().getCanonicalName() );

        } else {

            String qualifiedType = getParameterizedQualifiedClassName( identityInfo.getType().get() );

            String identityPropertyClass = String.format( "%s<%s, %s>", AbstractIdentityDeserializationInfo.class.getName(), type
                    .getParameterizedQualifiedSourceName(), qualifiedType );

            source.println( "new %s(\"%s\", %s.class, %s.class) {", identityPropertyClass, identityInfo.getPropertyName(), identityInfo
                    .getGenerator().getCanonicalName(), identityInfo.getScope().getCanonicalName() );
            source.indent();

            source.println( "@Override" );
            source.println( "protected %s<?> newDeserializer() {", JSON_DESERIALIZER_CLASS );
            source.indent();
            source.println( "return %s;", deserializerType.get().getInstance() );
            source.outdent();
            source.println( "}" );

            source.outdent();
            source.print( "}" );
        }
    }

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
            builder.add( "\n.addTypeInfo($T.class, $S)", JClassName.getRaw( entry.getKey() ), entry.getValue() );
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

    protected void generateCommonPropertyParameters( CodeBlock.Builder paramBuilder, PropertyInfo property ) {
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

    protected void buildBeanPropertySerializerBody( TypeSpec.Builder builder, JClassType beanType, PropertyInfo property, JSerializerType
            serializerType ) throws UnableToCompleteException {
        String paramName = "bean";
        Accessor getterAccessor = property.getGetterAccessor().get().getAccessor( paramName );

        MethodSpec.Builder newSerializerMethodBuilder = MethodSpec.methodBuilder( "newSerializer" )
                .addModifiers( Modifier.PROTECTED )
                .addAnnotation( Override.class )
                .addStatement( "return $L", serializerType.getInstance() );
        if ( property.isAnyGetter() ) {
            newSerializerMethodBuilder.returns( MapJsonSerializer.class );
        } else {
            newSerializerMethodBuilder.returns( ParameterizedTypeName
                    .get( ClassName.get( JsonSerializer.class ), WildcardTypeName.subtypeOf( Object.class ) ) );
        }
        builder.addMethod( newSerializerMethodBuilder.build() );

        Optional<MethodSpec> paramMethod = generatePropertySerializerParameters( property, serializerType );
        if ( paramMethod.isPresent() ) {
            builder.addMethod( paramMethod.get() );
        }

        builder.addMethod( MethodSpec.methodBuilder( "getValue" )
                        .addModifiers( Modifier.PUBLIC )
                        .addAnnotation( Override.class )
                        .returns( JClassName.get( true, property.getType() ) ) // the boxed type is specified so we can't return a primitive
                        .addParameter( JClassName.get( beanType ), paramName )
                        .addParameter( JsonSerializationContext.class, "ctx" )
                        .addStatement( "return $L", getterAccessor.getAccessor() )
                        .build()
        );

        if ( getterAccessor.getAdditionalMethod().isPresent() ) {
            builder.addMethod( getterAccessor.getAdditionalMethod().get() );
        }
    }

    protected Optional<MethodSpec> generatePropertySerializerParameters( PropertyInfo property, JSerializerType serializerType )
            throws UnableToCompleteException {

        if ( !property.getFormat().isPresent()
                && !property.getIgnoredProperties().isPresent()
                && !property.getIgnoreUnknown().isPresent()
                && !property.getIdentityInfo().isPresent()
                && !property.getTypeInfo().isPresent()
                && !property.getInclude().isPresent()
                && !property.isUnwrapped() ) {
            // none of the parameter are set so we don't generate the method
            return Optional.absent();
        }

        JClassType annotatedType = findFirstTypeToApplyPropertyAnnotation( serializerType );

        CodeBlock.Builder paramBuilder = CodeBlock.builder()
                .add( "return new $T()", JsonSerializerParameters.class )
                .indent()
                .indent();

        generateCommonPropertyParameters( paramBuilder, property );

        if ( property.getFormat().isPresent() ) {
            JsonFormat format = property.getFormat().get();
            if ( !Strings.isNullOrEmpty( format.timezone() ) && !JsonFormat.DEFAULT_TIMEZONE.equals( format.timezone() ) ) {
                java.util.TimeZone timeZoneJdk = java.util.TimeZone.getTimeZone( format.timezone() );
                // in java the offset is in milliseconds from timezone to GMT
                // in gwt the offset is in minutes from GMT to timezone
                // so we convert the milliseconds in minutes and invert the sign
                int timeZoneOffsetGwt = (timeZoneJdk.getRawOffset() / 1000 / 60) * -1;
                paramBuilder.add( "\n.setTimezone($T.createTimeZone($L))", TimeZone.class, timeZoneOffsetGwt );
            }
        }

        if ( property.getInclude().isPresent() ) {
            paramBuilder.add( "\n.setInclude($T.$L)", Include.class, property.getInclude().get().name() );
        }

        if ( property.getIdentityInfo().isPresent() ) {
            try {
                Optional<JSerializerType> identitySerializerType = getIdentitySerializerType( property.getIdentityInfo().get() );
                paramBuilder.add( "\n.setIdentityInfo($L)",
                        generateIdentifierSerializationInfo( annotatedType, property.getIdentityInfo().get(), identitySerializerType ) );
            } catch ( UnsupportedTypeException e ) {
                logger.log( Type.WARN, "Identity type is not supported. We ignore it." );
            }
        }

        if ( property.getTypeInfo().isPresent() ) {
            paramBuilder.add( "\n.setTypeInfo($L)", generateTypeInfo( property.getTypeInfo().get(), true ) );
        }

        if ( property.isUnwrapped() ) {
            paramBuilder.add( "\n.setUnwrapped(true)" );
        }

        paramBuilder.add( ";\n" )
                .unindent()
                .unindent();

        return Optional.of( MethodSpec.methodBuilder( "newParameters" )
                .addModifiers( Modifier.PROTECTED )
                .addAnnotation( Override.class )
                .returns( JsonSerializerParameters.class )
                .addCode( paramBuilder.build() )
                .build() );
    }
}
