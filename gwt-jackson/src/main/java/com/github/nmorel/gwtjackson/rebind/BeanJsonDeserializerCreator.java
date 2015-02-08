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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.github.nmorel.gwtjackson.client.JsonDeserializationContext;
import com.github.nmorel.gwtjackson.client.JsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.AnySetterDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.HasDeserializerAndParameters;
import com.github.nmorel.gwtjackson.client.deser.bean.IdentityDeserializationInfo;
import com.github.nmorel.gwtjackson.client.deser.bean.Instance;
import com.github.nmorel.gwtjackson.client.deser.bean.InstanceBuilder;
import com.github.nmorel.gwtjackson.client.deser.bean.SimpleStringMap;
import com.github.nmorel.gwtjackson.client.deser.bean.SubtypeDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.SubtypeDeserializer.BeanSubtypeDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.SubtypeDeserializer.DefaultSubtypeDeserializer;
import com.github.nmorel.gwtjackson.client.stream.JsonReader;
import com.github.nmorel.gwtjackson.client.stream.JsonToken;
import com.github.nmorel.gwtjackson.rebind.bean.BeanInfo;
import com.github.nmorel.gwtjackson.rebind.exception.UnsupportedTypeException;
import com.github.nmorel.gwtjackson.rebind.property.FieldAccessor;
import com.github.nmorel.gwtjackson.rebind.property.FieldAccessor.Accessor;
import com.github.nmorel.gwtjackson.rebind.property.PropertyInfo;
import com.github.nmorel.gwtjackson.rebind.type.JDeserializerType;
import com.github.nmorel.gwtjackson.rebind.writer.JClassName;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JAbstractMethod;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JConstructor;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.JParameter;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.thirdparty.guava.common.base.Joiner;
import com.google.gwt.thirdparty.guava.common.base.Optional;
import com.google.gwt.thirdparty.guava.common.collect.Collections2;
import com.google.gwt.thirdparty.guava.common.collect.ImmutableList;
import com.google.gwt.thirdparty.guava.common.collect.ImmutableMap;
import com.google.gwt.user.rebind.SourceWriter;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import static com.github.nmorel.gwtjackson.rebind.CreatorUtils.QUOTED_FUNCTION;
import static com.github.nmorel.gwtjackson.rebind.CreatorUtils.escapeString;
import static com.github.nmorel.gwtjackson.rebind.CreatorUtils.getDefaultValueForType;

/**
 * @author Nicolas Morel
 */
public class BeanJsonDeserializerCreator extends AbstractBeanJsonCreator {

    private static final String INSTANCE_BUILDER_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean.InstanceBuilder";

    private static final String INSTANCE_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean.Instance";

    private static final String INSTANCE_BUILDER_VARIABLE_FORMAT = "property_%d";

    public static final String DELEGATION_PARAM_NAME = "delegation";

    private static final String INSTANCE_BUILDER_DESERIALIZER_PREFIX = "deserializer_";

    private static final String BEAN_PROPERTY_DESERIALIZER_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean" + "" +
            ".BeanPropertyDeserializer";

    private static final String BACK_REFERENCE_PROPERTY_BEAN_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean.BackReferenceProperty";

    private static final String JSON_DESERIALIZER_PARAMETERS_CLASS = "com.github.nmorel.gwtjackson.client.JsonDeserializerParameters";

    public BeanJsonDeserializerCreator( TreeLogger logger, GeneratorContext context, RebindConfiguration configuration, JacksonTypeOracle
            typeOracle, JClassType beanType ) throws UnableToCompleteException {
        super( logger, context, configuration, typeOracle, beanType );
    }

    @Override
    protected boolean isSerializer() {
        return false;
    }

    @Override
    protected void buildSpecific() throws UnableToCompleteException, UnsupportedTypeException {

        if ( beanInfo.getCreatorMethod().isPresent() ) {
            typeBuilder.addMethod( buildInitInstanceBuilderMethod() );
        }

        // FIXME
//        // no need to generate properties for non instantiable class
//        if ( (beanInfo.getCreatorMethod().isPresent() && !beanInfo.isCreatorDelegation()) &&
//                (!properties.isEmpty() || beanInfo
//                        .getAnySetterPropertyInfo().isPresent()) ) {
//            generateInitPropertiesMethods( source, beanInfo, properties );
//        }
//
//        if ( beanInfo.getIdentityInfo().isPresent() ) {
//            try {
//                Optional<JDeserializerType> deserializerType = getIdentityDeserializerType( beanInfo.getIdentityInfo().get() );
//                generateInitIdentityInfoMethod( source, beanInfo, deserializerType );
//            } catch ( UnsupportedTypeException e ) {
//                logger.log( Type.WARN, "Identity type is not supported. We ignore it." );
//            }
//        }
//
//        if ( beanInfo.getTypeInfo().isPresent() ) {
//            generateInitTypeInfoMethod( source, beanInfo );
//        }
//
//        ImmutableList<JClassType> subtypes = filterSubtypes( beanInfo );
//        if ( !subtypes.isEmpty() ) {
//            generateInitMapSubtypeClassToDeserializerMethod( source, subtypes );
//        }
//
//        if ( beanInfo.isIgnoreUnknown() ) {
//            generateIsDefaultIgnoreUnknownMethod( source );
//        }
    }

    private MethodSpec buildInitInstanceBuilderMethod() throws UnableToCompleteException, UnsupportedTypeException {
        return MethodSpec.methodBuilder( "initInstanceBuilder" )
                .addModifiers( Modifier.PROTECTED )
                .addAnnotation( Override.class )
                .returns( ParameterizedTypeName.get( ClassName.get( InstanceBuilder.class ), JClassName.get( beanInfo.getType() ) ) )
                .addStatement( "return $L", generateInstanceBuilderClass() )
                .build();
    }

    private TypeSpec generateInstanceBuilderClass()
            throws UnableToCompleteException, UnsupportedTypeException {

        TypeSpec.Builder instanceBuilder = TypeSpec.anonymousClassBuilder( "" )
                .addSuperinterface( ParameterizedTypeName.get( ClassName.get( InstanceBuilder.class ), JClassName.get( beanInfo
                        .getType() ) ) );

        MethodSpec.Builder newInstanceMethodBuilder = MethodSpec.methodBuilder( "newInstance" )
                .addModifiers( Modifier.PUBLIC )
                .addAnnotation( Override.class )
                .returns( ParameterizedTypeName.get( ClassName.get( Instance.class ), JClassName.get( beanInfo.getType() ) ) )
                .addParameter( JsonReader.class, "reader" )
                .addParameter( JsonDeserializationContext.class, "ctx" )
                .addParameter( ParameterizedTypeName.get( Map.class, String.class, String.class ), "bufferedProperties" );

        MethodSpec.Builder createMethodBuilder = MethodSpec.methodBuilder( "create" )
                .addModifiers( Modifier.PRIVATE )
                .returns( JClassName.get( beanInfo.getType() ) );

// FIXME
//        if ( null != beanInfo.getCreatorParameters() && !beanInfo.getCreatorParameters().isEmpty() ) {
//            // for each constructor parameters, we initialize its deserializer.
//            int index = 0;
//            for ( Entry<String, JParameter> entry : beanInfo.getCreatorParameters().entrySet() ) {
//                String qualifiedTypeName = getParameterizedQualifiedClassName( entry.getValue().getType() );
//                String deserializerClass = String.format( "%s<%s, %s<%s>>", HasDeserializerAndParameters.class
//                        .getCanonicalName(), qualifiedTypeName, JSON_DESERIALIZER_CLASS, qualifiedTypeName );
//                source.println( "private final %s %s%s = new %s() {", deserializerClass, INSTANCE_BUILDER_DESERIALIZER_PREFIX, String
//                        .format( INSTANCE_BUILDER_VARIABLE_FORMAT, index++ ), deserializerClass );
//
//                generateCommonPropertyDeserializerBody( source, properties.get( entry.getKey() ) );
//                source.println( "};" );
//            }
//        }

        if ( beanInfo.isCreatorDefaultConstructor() ) {
            generateInstanceBuilderForDefaultConstructor( instanceBuilder, newInstanceMethodBuilder, createMethodBuilder );
        } else if ( beanInfo.isCreatorDelegation() ) {
            generateInstanceBuilderForConstructorOrFactoryMethodDelegation( instanceBuilder, newInstanceMethodBuilder,
                    createMethodBuilder );
        } else {
            generateInstanceBuilderForConstructorOrFactoryMethod( instanceBuilder, newInstanceMethodBuilder, createMethodBuilder );
        }

        instanceBuilder.addMethod( newInstanceMethodBuilder.build() );
        instanceBuilder.addMethod( createMethodBuilder.build() );
        return instanceBuilder.build();
    }

    /**
     * Generate the instance builder class body for a default constructor. We directly instantiate the bean at the builder creation and we
     * set the properties to it
     *
     * @param instanceBuilder builder for the {@link InstanceBuilder} implementation
     * @param newInstanceMethodBuilder builder for the {@link InstanceBuilder#newInstance(JsonReader, JsonDeserializationContext, Map)}
     * method
     * @param createMethodBuilder builder for the create method
     */
    private void generateInstanceBuilderForDefaultConstructor( TypeSpec.Builder instanceBuilder, MethodSpec.Builder
            newInstanceMethodBuilder, MethodSpec.Builder createMethodBuilder ) {
        newInstanceMethodBuilder.addStatement( "return new $T(create(), bufferedProperties)",
                ParameterizedTypeName.get( ClassName.get( Instance.class ), JClassName.get( beanInfo.getType() ) ) );
    }

    /**
     * Generate the instance builder class body for a constructor with parameters or factory method. We will declare all the fields and
     * instanciate the bean only on build() method when all properties have been deserialiazed
     *
     * @param instanceBuilder builder for the {@link InstanceBuilder} implementation
     * @param newInstanceMethodBuilder builder for the {@link InstanceBuilder#newInstance(JsonReader, JsonDeserializationContext, Map)}
     * method
     * @param createMethodBuilder builder for the create method
     */
    private void generateInstanceBuilderForConstructorOrFactoryMethod( TypeSpec.Builder instanceBuilder, MethodSpec.Builder
            newInstanceMethodBuilder, MethodSpec.Builder createMethodBuilder ) throws UnableToCompleteException {
// FIXME
//        // we don't use directly the property name to name our variable in case it contains invalid character
//        ImmutableMap.Builder<String, String> propertyNameToVariableBuilder = ImmutableMap.builder();
//
//        List<String> requiredProperties = new ArrayList<String>();
//        int propertyIndex = 0;
//        for ( String name : beanInfo.getCreatorParameters().keySet() ) {
//            String variableName = String.format( INSTANCE_BUILDER_VARIABLE_FORMAT, propertyIndex++ );
//            propertyNameToVariableBuilder.put( name, variableName );
//            PropertyInfo propertyInfo = properties.get( name );
//
//            source.println( "%s %s = %s; // %s", propertyInfo.getType()
//                    .getParameterizedQualifiedSourceName(), variableName, getDefaultValueForType( propertyInfo.getType() ), name );
//
//            if ( propertyInfo.isRequired() ) {
//                requiredProperties.add( name );
//            }
//        }
//
//        ImmutableMap<String, String> propertyNameToVariable = propertyNameToVariableBuilder.build();
//
//        source.println();
//
//        source.println( "int nbParamToFind = %d;", beanInfo.getCreatorParameters().size() );
//
//        if ( !requiredProperties.isEmpty() ) {
//            String requiredList = Joiner.on( ", " ).join( Collections2.transform( requiredProperties, QUOTED_FUNCTION ) );
//            source.println( "%s<String> requiredProperties = new %s<String>(%s.asList(%s));", Set.class.getName(), HashSet.class
//                    .getName(), Arrays.class.getName(), requiredList );
//        }
//
//        source.println();
//
//        source.println( "if(null != bufferedProperties) {" );
//        source.indent();
//        source.println( "String value;" );
//        for ( String name : beanInfo.getCreatorParameters().keySet() ) {
//            String variableName = propertyNameToVariable.get( name );
//            PropertyInfo propertyInfo = properties.get( name );
//
//            source.println();
//            source.println( "value = bufferedProperties.remove(\"%s\");", name );
//            source.println( "if(null != value) {" );
//            source.indent();
//            source.println( "%s = %s%s.deserialize(ctx.newJsonReader(value), ctx);", variableName, INSTANCE_BUILDER_DESERIALIZER_PREFIX,
//                    variableName );
//            source.println( "nbParamToFind--;" );
//            if ( propertyInfo.isRequired() ) {
//                source.println( "requiredProperties.remove(\"%s\");", name );
//            }
//            source.outdent();
//            source.println( "}" );
//        }
//        source.outdent();
//        source.println( "}" );
//
//        source.println();
//
//        source.println( "String name;" );
//        source.println( "while (nbParamToFind > 0 && %s.NAME == reader.peek()) {", JsonToken.class.getName() );
//        source.indent();
//
//        source.println( "name = reader.nextName();" );
//        source.println();
//
//        for ( String name : beanInfo.getCreatorParameters().keySet() ) {
//            String variableName = propertyNameToVariable.get( name );
//            PropertyInfo propertyInfo = properties.get( name );
//
//            source.println( "if(\"%s\".equals(name)) {", name );
//            source.indent();
//            source.println( "%s = %s%s.deserialize(reader, ctx);", variableName, INSTANCE_BUILDER_DESERIALIZER_PREFIX, variableName );
//            source.println( "nbParamToFind--;" );
//            if ( propertyInfo.isRequired() ) {
//                source.println( "requiredProperties.remove(\"%s\");", name );
//            }
//            source.println( "continue;" );
//            source.outdent();
//            source.println( "}" );
//        }
//
//        source.println();
//        source.println( "if(null == bufferedProperties) {" );
//        source.indent();
//        source.println( "bufferedProperties = new %s<String, String>();", HashMap.class.getName() );
//        source.outdent();
//        source.println( "}" );
//        source.println( "bufferedProperties.put( name, reader.nextValue() );" );
//
//        source.outdent();
//        source.println( "}" );
//
//        source.println();
//
//        if ( !requiredProperties.isEmpty() ) {
//            source.println( "if(!requiredProperties.isEmpty()) {" );
//            source.indent();
//            source.println( "throw ctx.traceError( \"Required properties are missing : \" + requiredProperties, reader );" );
//            source.outdent();
//            source.println( "}" );
//            source.println();
//        }
//
//        String parameters = Joiner.on( ", " ).join( propertyNameToVariable.values() );
//        source.println( "return new %s<%s>( create(%s), bufferedProperties );", INSTANCE_CLASS, beanInfo.getType()
//                .getParameterizedQualifiedSourceName(), parameters );
    }

    /**
     * Generate the instance builder class body for a constructor or factory method with delegation.
     *
     * @param instanceBuilder builder for the {@link InstanceBuilder} implementation
     * @param newInstanceMethodBuilder builder for the {@link InstanceBuilder#newInstance(JsonReader, JsonDeserializationContext, Map)}
     * method
     * @param createMethodBuilder builder for the create method
     */
    private void generateInstanceBuilderForConstructorOrFactoryMethodDelegation( TypeSpec.Builder instanceBuilder, MethodSpec.Builder
            newInstanceMethodBuilder, MethodSpec.Builder createMethodBuilder ) throws
            UnsupportedTypeException {

        // FIXME
//        Entry<String, JParameter> entry = beanInfo.getCreatorParameters().entrySet().iterator().next();
//
//        FieldSpec deserializerField = FieldSpec.builder( ParameterizedTypeName.get( ClassName.get( JsonDeserializer.class), JClassName.get( entry.getValue().getType() ) ) );
//
//        String param = String.format( "%s%s.deserialize(reader, ctx)", INSTANCE_BUILDER_DESERIALIZER_PREFIX, String
//                .format( INSTANCE_BUILDER_VARIABLE_FORMAT, 0 ) );
//        source.println( "return new %s<%s>(create(%s), bufferedProperties);", INSTANCE_CLASS, beanInfo.getType()
//                .getParameterizedQualifiedSourceName(), param );
    }

    private void generateInstanceBuilderCreateMethod( SourceWriter source, BeanInfo info, ImmutableMap<String, PropertyInfo> properties ) {
        JAbstractMethod method = info.getCreatorMethod().get();

        StringBuilder parametersBuilder = new StringBuilder();
        StringBuilder parametersNameBuilder = new StringBuilder();
        int index = 0;
        for ( Map.Entry<String, JParameter> parameterEntry : info.getCreatorParameters().entrySet() ) {
            if ( parametersBuilder.length() > 0 ) {
                parametersBuilder.append( ", " );
                parametersNameBuilder.append( ", " );
            }
            PropertyInfo property = properties.get( parameterEntry.getKey() );

            String variableName = String.format( INSTANCE_BUILDER_VARIABLE_FORMAT, index++ );
            parametersBuilder.append( property.getType().getParameterizedQualifiedSourceName() ).append( " " ).append( variableName );
            parametersNameBuilder.append( variableName );
        }

        if ( method.isPrivate() || (!method.isPublic() && !mapperInfo.isSamePackage()) ) {
            // private method, we use jsni
            source.println( "private native %s create(%s) /*-{", info.getType().getParameterizedQualifiedSourceName(), parametersBuilder
                    .toString() );
            source.indent();

            if ( null != method.isConstructor() ) {
                JConstructor constructor = method.isConstructor();
                source.println( "return %s(%s);", constructor.getJsniSignature(), parametersNameBuilder.toString() );
            } else {
                JMethod factory = method.isMethod();
                source.println( "return %s(%s);", factory.getJsniSignature(), parametersNameBuilder.toString() );
            }

            source.outdent();
            source.println( "}-*/;" );
        } else {
            source.println( "private %s create(%s) {", info.getType().getParameterizedQualifiedSourceName(), parametersBuilder.toString() );
            source.indent();

            if ( null != method.isConstructor() ) {
                source.println( "return new %s(%s);", info.getType().getParameterizedQualifiedSourceName(), parametersNameBuilder
                        .toString() );
            } else {
                source.println( "return %s.%s(%s);", info.getType().getQualifiedSourceName(), method.getName(), parametersNameBuilder
                        .toString() );
            }

            source.outdent();
            source.println( "}" );
        }
    }

    private void generateInitPropertiesMethods( SourceWriter source, BeanInfo beanInfo, Map<String, PropertyInfo> properties ) throws
            UnableToCompleteException {

        List<PropertyInfo> ignoredProperties = new ArrayList<PropertyInfo>();
        List<PropertyInfo> requiredProperties = new ArrayList<PropertyInfo>();
        Map<PropertyInfo, JDeserializerType> deserializerProperties = new LinkedHashMap<PropertyInfo, JDeserializerType>();
        List<PropertyInfo> backReferenceProperties = new ArrayList<PropertyInfo>();

        for ( PropertyInfo property : properties.values() ) {
            if ( null != beanInfo.getCreatorParameters() && beanInfo.getCreatorParameters().containsKey( property.getPropertyName() ) ) {
                // properties used in constructor are deserialized inside instance builder
                continue;
            }

            if ( property.isIgnored() ) {
                ignoredProperties.add( property );
                continue;
            }

            if ( !property.getSetterAccessor().isPresent() ) {
                // there is no setter visible
                continue;
            }

            if ( !property.getBackReference().isPresent() ) {
                try {
                    JDeserializerType deserializerType = getJsonDeserializerFromType( property.getType() );
                    deserializerProperties.put( property, deserializerType );
                    if ( property.isRequired() ) {
                        requiredProperties.add( property );
                    }
                } catch ( UnsupportedTypeException e ) {
                    logger.log( Type.WARN, "Property '" + property.getPropertyName() + "' is ignored" );
                    ignoredProperties.add( property );
                }
            } else {
                backReferenceProperties.add( property );
            }
        }

        if ( !deserializerProperties.isEmpty() ) {
            generateInitDeserializersMethod( source, beanInfo, deserializerProperties );
            source.println();
        }

        if ( !backReferenceProperties.isEmpty() ) {
            generateInitBackReferenceDeserializersMethod( source, beanInfo, backReferenceProperties );
            source.println();
        }

        if ( !ignoredProperties.isEmpty() ) {
            generateInitIgnoredPropertiesMethod( source, ignoredProperties );
            source.println();
        }

        if ( !requiredProperties.isEmpty() ) {
            generateInitRequiredPropertiesMethod( source, requiredProperties );
        }

        if ( beanInfo.getAnySetterPropertyInfo().isPresent() ) {
            generateInitAnySetterDeserializerMethod( source, beanInfo, beanInfo.getAnySetterPropertyInfo().get() );
            source.println();
        }
    }

    private void generateInitAnySetterDeserializerMethod( SourceWriter source, BeanInfo beanInfo, PropertyInfo anySetterPropertyInfo )
            throws UnableToCompleteException {

        FieldAccessor fieldAccessor = anySetterPropertyInfo.getSetterAccessor().get();
        JType type = fieldAccessor.getMethod().get().getParameterTypes()[1];

        JDeserializerType deserializerType;
        try {
            deserializerType = getJsonDeserializerFromType( type );
        } catch ( UnsupportedTypeException e ) {
            logger.log( Type.WARN, "Method '" + fieldAccessor.getMethod().get()
                    .getName() + "' annotated with @JsonAnySetter has an unsupported type" );
            return;
        }

        source.println( "@Override" );
        source.println( "protected %s<%s, ?> initAnySetterDeserializer() {", AnySetterDeserializer.class.getCanonicalName(), beanInfo
                .getType().getParameterizedQualifiedSourceName() );
        source.indent();

        source.print( "return " );
        generateDeserializer( source, beanInfo, anySetterPropertyInfo, type, deserializerType );
        source.println( ";" );

        source.outdent();
        source.println( "}" );
        source.println();
    }

    private void generateInitDeserializersMethod( SourceWriter source, BeanInfo beanInfo, Map<PropertyInfo, JDeserializerType> properties
    ) throws UnableToCompleteException {
        String resultType = String.format( "%s<%s<%s, ?>>", SimpleStringMap.class
                .getCanonicalName(), BEAN_PROPERTY_DESERIALIZER_CLASS, beanInfo.getType().getParameterizedQualifiedSourceName() );

        source.println( "@Override" );
        source.println( "protected %s initDeserializers() {", resultType );
        source.indent();

        source.println( "%s map = %s.createObject().cast();", resultType, SimpleStringMap.class.getCanonicalName() );
        source.println();

        for ( Entry<PropertyInfo, JDeserializerType> entry : properties.entrySet() ) {
            PropertyInfo property = entry.getKey();
            JDeserializerType deserializerType = entry.getValue();

            source.print( "map.put(\"%s\", ", escapeString( property.getPropertyName() ) );

            generateDeserializer( source, beanInfo, property, property.getType(), deserializerType );

            source.println( ");" );
            source.println();
        }

        source.println( "return map;" );
        source.outdent();
        source.println( "}" );
    }

    private void generateDeserializer( SourceWriter source, BeanInfo beanInfo, PropertyInfo property, JType propertyType,
                                       JDeserializerType deserializerType ) throws UnableToCompleteException {
        Accessor accessor = property.getSetterAccessor().get().getAccessor( "bean" );
        String superclass = BEAN_PROPERTY_DESERIALIZER_CLASS;
        if ( property.isAnySetter() ) {
            superclass = AnySetterDeserializer.class.getCanonicalName();
        }

        source.println( "new %s<%s, %s>() {", superclass, beanInfo.getType()
                .getParameterizedQualifiedSourceName(), getParameterizedQualifiedClassName( propertyType ) );

        source.indent();

        generateCommonPropertyDeserializerBody( source, property, deserializerType );

        source.println();

        source.println( "@Override" );
        source.print( "public void setValue(%s bean, ", beanInfo.getType().getParameterizedQualifiedSourceName() );
        if ( property.isAnySetter() ) {
            source.print( "String propertyName, " );
        }
        source.println( "%s value, %s ctx) {", getParameterizedQualifiedClassName( propertyType ), JSON_DESERIALIZATION_CONTEXT_CLASS );
        source.indent();

        if ( property.isAnySetter() ) {
            source.println( accessor.getAccessor() + ";", "propertyName", "value" );
        } else {
            source.println( accessor.getAccessor() + ";", "value" );
        }

        if ( property.getManagedReference().isPresent() ) {
            source.println( "getDeserializer().setBackReference(\"%s\", bean, value, ctx);", property.getManagedReference().get() );
        }

        source.outdent();
        source.println( "}" );

        if ( accessor.getAdditionalMethod().isPresent() ) {
            source.println();
            // FIXME
            //accessor.getAdditionalMethod().get().write( source );
        }

        source.outdent();
        source.print( "}" );
    }

    private void generateCommonPropertyDeserializerBody( SourceWriter source, PropertyInfo property ) throws UnableToCompleteException,
            UnsupportedTypeException {
        generateCommonPropertyDeserializerBody( source, property, getJsonDeserializerFromType( property.getType() ) );
    }

    private void generateCommonPropertyDeserializerBody( SourceWriter source, PropertyInfo property, JDeserializerType deserializerType )
            throws UnableToCompleteException {
        source.println( "@Override" );
        source.println( "protected %s<?> newDeserializer() {", JSON_DESERIALIZER_CLASS );
        source.indent();
        source.println( "return %s;", deserializerType.getInstance() );
        source.outdent();
        source.println( "}" );

        generatePropertyDeserializerParameters( source, property, deserializerType );
    }

    private void generatePropertyDeserializerParameters( SourceWriter source, PropertyInfo property, JDeserializerType deserializerType )
            throws UnableToCompleteException {

        if ( property.getFormat().isPresent() || property.getIgnoredProperties().isPresent() || property.getIgnoreUnknown().isPresent() ||
                property.getIdentityInfo().isPresent() || property.getTypeInfo().isPresent() ) {

            JClassType annotatedType = findFirstTypeToApplyPropertyAnnotation( deserializerType );

            source.println();

            source.println( "@Override" );
            source.println( "protected %s newParameters() {", JSON_DESERIALIZER_PARAMETERS_CLASS );
            source.indent();
            source.print( "return new %s()", JSON_DESERIALIZER_PARAMETERS_CLASS );

            source.indent();
            // FIXME
//            generateCommonPropertyParameters( source, property, deserializerType );

            if ( property.getIgnoreUnknown().isPresent() ) {
                source.println();
                source.print( ".setIgnoreUnknown(%s)", Boolean.toString( property.getIgnoreUnknown().get() ) );
            }

            if ( property.getIdentityInfo().isPresent() ) {
                try {
                    Optional<JDeserializerType> identityDeserializerType = getIdentityDeserializerType( property.getIdentityInfo().get() );
                    source.println();
                    source.print( ".setIdentityInfo(" );
                    generateIdentifierDeserializationInfo( source, annotatedType, property.getIdentityInfo()
                            .get(), identityDeserializerType );
                    source.print( ")" );
                } catch ( UnsupportedTypeException e ) {
                    logger.log( Type.WARN, "Identity type is not supported. We ignore it." );
                }
            }

            if ( property.getTypeInfo().isPresent() ) {
                source.println();
                source.print( ".setTypeInfo(" );
                // FIXME
                //generateTypeInfo( source, property.getTypeInfo().get(), false );
                source.print( ")" );
            }

            source.println( ";" );
            source.outdent();

            source.outdent();
            source.println( "}" );
        }
    }

    private void generateInitBackReferenceDeserializersMethod( SourceWriter source, BeanInfo beanInfo, List<PropertyInfo> properties )
            throws UnableToCompleteException {
        String resultType = String.format( "%s<%s<%s, ?>>", SimpleStringMap.class
                .getCanonicalName(), BACK_REFERENCE_PROPERTY_BEAN_CLASS, beanInfo.getType().getParameterizedQualifiedSourceName() );

        source.println( "@Override" );
        source.println( "protected %s initBackReferenceDeserializers() {", resultType );
        source.indent();
        source.println( "%s map = %s.createObject().cast();", resultType, SimpleStringMap.class.getCanonicalName() );
        for ( PropertyInfo property : properties ) {

            Accessor accessor = property.getSetterAccessor().get().getAccessor( "bean" );

            // this is a back reference, we add the special back reference property that will be called by the parent
            source.println( "map.put(\"%s\", new %s<%s, %s>() {", escapeString( property.getBackReference()
                    .get() ), BACK_REFERENCE_PROPERTY_BEAN_CLASS, beanInfo.getType()
                    .getParameterizedQualifiedSourceName(), getParameterizedQualifiedClassName( property.getType() ) );

            source.indent();
            source.println( "@Override" );
            source.println( "public void setBackReference(%s bean, %s reference, %s ctx) {", beanInfo.getType()
                    .getParameterizedQualifiedSourceName(), property.getType()
                    .getParameterizedQualifiedSourceName(), JSON_DESERIALIZATION_CONTEXT_CLASS );
            source.indent();

            source.println( accessor.getAccessor() + ";", "reference" );

            source.outdent();
            source.println( "}" );

            if ( accessor.getAdditionalMethod().isPresent() ) {
                source.println();
                // FIXME
                // accessor.getAdditionalMethod().get().write( source );
            }

            source.outdent();
            source.println( "});" );
            source.println();
        }
        source.println( "return map;" );
        source.outdent();
        source.println( "}" );
    }

    private void generateInitIgnoredPropertiesMethod( SourceWriter source, List<PropertyInfo> properties ) {
        source.println( "@Override" );
        source.println( "protected %s<%s> initIgnoredProperties() {", Set.class.getCanonicalName(), String.class.getCanonicalName() );
        source.indent();
        source.println( "%s<%s> col = new %s<%s>(%s);", HashSet.class.getCanonicalName(), String.class.getCanonicalName(), HashSet.class
                .getCanonicalName(), String.class.getCanonicalName(), properties.size() );
        for ( PropertyInfo property : properties ) {
            source.println( "col.add(\"%s\");", property.getPropertyName() );
        }
        source.println( "return col;" );
        source.outdent();
        source.println( "}" );
    }

    private void generateInitRequiredPropertiesMethod( SourceWriter source, List<PropertyInfo> properties ) {
        source.println( "@Override" );
        source.println( "protected %s<%s> initRequiredProperties() {", Set.class.getCanonicalName(), String.class.getCanonicalName() );
        source.indent();
        source.println( "%s<%s> col = new %s<%s>(%s);", HashSet.class.getCanonicalName(), String.class.getCanonicalName(), HashSet.class
                .getCanonicalName(), String.class.getCanonicalName(), properties.size() );
        for ( PropertyInfo property : properties ) {
            source.println( "col.add(\"%s\");", property.getPropertyName() );
        }
        source.println( "return col;" );
        source.outdent();
        source.println( "}" );
    }

    private void generateInitIdentityInfoMethod( SourceWriter source, BeanInfo beanInfo, Optional<JDeserializerType> deserializerType )
            throws UnableToCompleteException {
        source.println( "@Override" );
        source.println( "protected %s<%s> initIdentityInfo() {", IdentityDeserializationInfo.class.getCanonicalName(), beanInfo.getType()
                .getParameterizedQualifiedSourceName() );
        source.indent();
        source.print( "return " );
        generateIdentifierDeserializationInfo( source, beanInfo.getType(), beanInfo.getIdentityInfo().get(), deserializerType );
        source.println( ";" );
        source.outdent();
        source.println( "}" );
    }

    private void generateInitTypeInfoMethod( SourceWriter source, BeanInfo beanInfo ) throws UnableToCompleteException {
        source.println( "@Override" );
        source.println( "protected %s<%s> initTypeInfo() {", TYPE_DESERIALIZATION_INFO_CLASS, beanInfo.getType()
                .getParameterizedQualifiedSourceName() );
        source.indent();
        source.print( "return " );
        //FIXME
//        generateTypeInfo( source, beanInfo.getTypeInfo().get(), false );
        source.println( ";" );
        source.outdent();
        source.println( "}" );
    }

    private void generateInitMapSubtypeClassToDeserializerMethod( SourceWriter source, ImmutableList<JClassType> subtypes ) throws
            UnableToCompleteException {

        String mapTypes = String.format( "<%s, %s>", Class.class.getCanonicalName(), SubtypeDeserializer.class.getName() );
        String resultType = String.format( "%s%s", Map.class.getCanonicalName(), mapTypes );

        source.println( "@Override" );
        source.println( "protected %s initMapSubtypeClassToDeserializer() {", resultType );

        source.indent();

        source.println( "%s map = new %s%s(%s);", resultType, IdentityHashMap.class.getCanonicalName(), mapTypes, subtypes.size() );
        source.println();

        for ( JClassType subtype : subtypes ) {

            JDeserializerType deserializerType;
            try {
                deserializerType = getJsonDeserializerFromType( subtype, true );
            } catch ( UnsupportedTypeException e ) {
                logger.log( Type.WARN, "Subtype '" + subtype.getQualifiedSourceName() + "' is not supported. We ignore it." );
                continue;
            }

            String subtypeClass;
            String deserializerClass;
            if ( configuration.getDeserializer( subtype ).isPresent() || null != subtype.isEnum() ) {
                subtypeClass = DefaultSubtypeDeserializer.class.getCanonicalName();
                deserializerClass = String.format( "%s<?>", JsonDeserializer.class.getName() );
            } else {
                subtypeClass = BeanSubtypeDeserializer.class.getCanonicalName();
                deserializerClass = String.format( "%s<?>", ABSTRACT_BEAN_JSON_DESERIALIZER_CLASS );
            }

            source.println( "map.put( %s.class, new %s() {", subtype.getQualifiedSourceName(), subtypeClass );
            source.indent();

            source.println( "@Override" );
            source.println( "protected %s newDeserializer() {", deserializerClass );
            source.indent();
            source.println( "return %s;", deserializerType.getInstance() );
            source.outdent();
            source.println( "}" );

            source.outdent();
            source.println( "});" );
            source.println();
        }

        source.println( "return map;" );

        source.outdent();
        source.println( "}" );
    }

    private void generateIsDefaultIgnoreUnknownMethod( SourceWriter source ) {
        source.println( "@Override" );
        source.println( "protected boolean isDefaultIgnoreUnknown() {" );
        source.indent();
        source.println( "return true;" );
        source.outdent();
        source.println( "}" );
    }
}
