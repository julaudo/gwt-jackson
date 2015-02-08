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
import java.util.Iterator;

import com.github.nmorel.gwtjackson.client.JsonDeserializer;
import com.github.nmorel.gwtjackson.client.JsonSerializer;
import com.github.nmorel.gwtjackson.client.deser.EnumJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.array.ArrayJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.array.ArrayJsonDeserializer.ArrayCreator;
import com.github.nmorel.gwtjackson.client.deser.array.dd.Array2dJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.array.dd.Array2dJsonDeserializer.Array2dCreator;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractBeanJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.map.key.EnumKeyDeserializer;
import com.github.nmorel.gwtjackson.client.deser.map.key.KeyDeserializer;
import com.github.nmorel.gwtjackson.client.ser.EnumJsonSerializer;
import com.github.nmorel.gwtjackson.client.ser.array.ArrayJsonSerializer;
import com.github.nmorel.gwtjackson.client.ser.array.dd.Array2dJsonSerializer;
import com.github.nmorel.gwtjackson.client.ser.bean.AbstractBeanJsonSerializer;
import com.github.nmorel.gwtjackson.client.ser.map.key.EnumKeySerializer;
import com.github.nmorel.gwtjackson.client.ser.map.key.KeySerializer;
import com.github.nmorel.gwtjackson.rebind.RebindConfiguration.MapperInstance;
import com.github.nmorel.gwtjackson.rebind.RebindConfiguration.MapperType;
import com.github.nmorel.gwtjackson.rebind.exception.UnsupportedTypeException;
import com.github.nmorel.gwtjackson.rebind.type.JDeserializerType;
import com.github.nmorel.gwtjackson.rebind.type.JMapperType;
import com.github.nmorel.gwtjackson.rebind.type.JSerializerType;
import com.github.nmorel.gwtjackson.rebind.writer.JClassName;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JArrayType;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JEnumType;
import com.google.gwt.core.ext.typeinfo.JGenericType;
import com.google.gwt.core.ext.typeinfo.JParameterizedType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.core.ext.typeinfo.JTypeParameter;
import com.google.gwt.thirdparty.guava.common.base.Optional;
import com.google.gwt.thirdparty.guava.common.collect.ImmutableList;
import com.google.gwt.user.rebind.AbstractSourceCreator;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

/**
 * @author Nicolas Morel
 */
public abstract class AbstractCreator extends AbstractSourceCreator {

    protected static final String TYPE_PARAMETER_DESERIALIZER_FIELD_NAME = "deserializer%d";

    protected static final String TYPE_PARAMETER_SERIALIZER_FIELD_NAME = "serializer%d";

    protected final TreeLogger logger;

    protected final GeneratorContext context;

    protected final RebindConfiguration configuration;

    protected final JacksonTypeOracle typeOracle;

    protected AbstractCreator( TreeLogger logger, GeneratorContext context, RebindConfiguration configuration, JacksonTypeOracle
            typeOracle ) {
        this.logger = logger;
        this.context = context;
        this.configuration = configuration;
        this.typeOracle = typeOracle;
    }

    protected PrintWriter getPrintWriter( String packageName, String className ) {
        return context.tryCreate( logger, packageName, className );
    }

    protected void write( String packageName, TypeSpec type, PrintWriter printWriter ) throws UnableToCompleteException {
        try {
            JavaFile.builder( packageName, type )
                    .skipJavaLangImports( true )
                    .build()
                    .writeTo( printWriter );
            context.commit( logger, printWriter );
        } catch ( IOException e ) {
            logger.log( TreeLogger.Type.ERROR, "Error writing the file " + packageName + "." + type.name, e );
            throw new UnableToCompleteException();
        }
    }

    protected abstract Optional<BeanJsonMapperInfo> getMapperInfo();

    /**
     * Build the string that instantiate a {@link JsonSerializer} for the given type. If the type is a bean,
     * the implementation of {@link AbstractBeanJsonSerializer} will
     * be created.
     *
     * @param type type
     *
     * @return the code instantiating the {@link JsonSerializer}. Examples:
     * <ul>
     * <li>ctx.getIntegerSerializer()</li>
     * <li>new org.PersonBeanJsonSerializer()</li>
     * </ul>
     */
    protected JSerializerType getJsonSerializerFromType( JType type ) throws UnableToCompleteException, UnsupportedTypeException {
        return getJsonSerializerFromType( type, false );
    }

    /**
     * Build the string that instantiate a {@link JsonSerializer} for the given type. If the type is a bean,
     * the implementation of {@link AbstractBeanJsonSerializer} will
     * be created.
     *
     * @param type type
     * @param subtype true if the serializer is for a subtype
     *
     * @return the code instantiating the {@link JsonSerializer}. Examples:
     * <ul>
     * <li>ctx.getIntegerSerializer()</li>
     * <li>new org.PersonBeanJsonSerializer()</li>
     * </ul>
     */
    protected JSerializerType getJsonSerializerFromType( JType type, boolean subtype ) throws UnableToCompleteException,
            UnsupportedTypeException {
        JSerializerType.Builder builder = new JSerializerType.Builder().type( type );
        if ( null != type.isWildcard() ) {
            // we use the base type to find the serializer to use
            type = type.isWildcard().getBaseType();
        }

        if ( null != type.isRawType() ) {
            type = type.isRawType().getBaseType();
        }

        JTypeParameter typeParameter = type.isTypeParameter();
        if ( null != typeParameter ) {
            if ( !subtype || typeParameter.getDeclaringClass() == getMapperInfo().get().getType() ) {
                return builder.instance( CodeBlock.builder().add( String.format( TYPE_PARAMETER_SERIALIZER_FIELD_NAME, typeParameter
                        .getOrdinal() ) )
                        .build() ).build();
            } else {
                type = typeParameter.getBaseType();
            }
        }

        Optional<MapperInstance> configuredSerializer = configuration.getSerializer( type );
        if ( configuredSerializer.isPresent() ) {
            if ( null != type.isParameterized() || null != type.isGenericType() ) {
                JClassType[] typeArgs;
                if ( null != type.isGenericType() ) {
                    typeArgs = type.isGenericType().asParameterizedByWildcards().getTypeArgs();
                } else {
                    typeArgs = type.isParameterized().getTypeArgs();
                }

                ImmutableList.Builder<JSerializerType> parametersSerializerBuilder = ImmutableList.builder();
                for ( int i = 0; i < typeArgs.length; i++ ) {
                    JSerializerType parameterSerializerType;
                    if ( MapperType.KEY_SERIALIZER == configuredSerializer.get().getParameters()[i] ) {
                        parameterSerializerType = getKeySerializerFromType( typeArgs[i] );
                    } else {
                        parameterSerializerType = getJsonSerializerFromType( typeArgs[i], subtype );
                    }
                    parametersSerializerBuilder.add( parameterSerializerType );
                }
                ImmutableList<JSerializerType> parametersSerializer = parametersSerializerBuilder.build();
                builder.parameters( parametersSerializer );
                builder.instance( methodCallCode( configuredSerializer.get(), parametersSerializer ) );

            } else {
                builder.instance( methodCallCode( configuredSerializer.get() ) );
            }
            return builder.build();
        }

        if ( typeOracle.isJavaScriptObject( type ) ) {
            // it's a JSO and the user didn't give a custom serializer. We use the default one.
            configuredSerializer = configuration.getSerializer( typeOracle.getJavaScriptObject() );
            return builder.instance( methodCallCode( configuredSerializer.get() ) ).build();
        }

        JEnumType enumType = type.isEnum();
        if ( null != enumType ) {
            return builder.instance( CodeBlock.builder()
                    .add( "$T.<$T<$T>>getInstance()", EnumJsonSerializer.class, EnumJsonSerializer.class, JClassName.get( enumType ) )
                    .build() ).build();
        }

        if ( Enum.class.getName().equals( type.getQualifiedSourceName() ) ) {
            return builder.instance( CodeBlock.builder().add( "$T.getInstance()", EnumJsonSerializer.class ).build() ).build();
        }

        JArrayType arrayType = type.isArray();
        if ( null != arrayType ) {
            Class arraySerializer;
            if ( arrayType.getRank() == 1 ) {
                // one dimension array
                arraySerializer = ArrayJsonSerializer.class;
            } else if ( arrayType.getRank() == 2 ) {
                // two dimension array
                arraySerializer = Array2dJsonSerializer.class;
            } else {
                // more dimensions are not supported
                String message = "Arrays with 3 or more dimensions are not supported";
                logger.log( TreeLogger.Type.WARN, message );
                throw new UnsupportedTypeException( message );
            }
            JSerializerType parameterSerializerType = getJsonSerializerFromType( arrayType.getLeafType(), subtype );
            builder.parameters( ImmutableList.of( parameterSerializerType ) );
            builder.instance( CodeBlock.builder().add( "$T.newInstance($L)", arraySerializer, parameterSerializerType
                    .getInstance() )
                    .build() );
            return builder.build();
        }

        if ( null != type.isAnnotation() ) {
            String message = "Annotations are not supported";
            logger.log( TreeLogger.Type.WARN, message );
            throw new UnsupportedTypeException( message );
        }

        JClassType classType = type.isClassOrInterface();
        if ( null != classType ) {
            // it's a bean
            JClassType baseClassType = classType;
            JParameterizedType parameterizedType = classType.isParameterized();
            if ( null != parameterizedType ) {
                // it's a bean with generics, we create a serializer based on generic type
                baseClassType = parameterizedType.getBaseType();
            }

            BeanJsonSerializerCreator beanJsonSerializerCreator = new BeanJsonSerializerCreator( logger
                    .branch( Type.DEBUG, "Creating serializer for " + baseClassType
                            .getQualifiedSourceName() ), context, configuration, typeOracle, baseClassType );
            BeanJsonMapperInfo mapperInfo = beanJsonSerializerCreator.create();

            ImmutableList<? extends JType> typeParameters = getTypeParameters( classType, subtype );
            ImmutableList.Builder<JSerializerType> parametersSerializerBuilder = ImmutableList.builder();
            for ( JType argType : typeParameters ) {
                parametersSerializerBuilder.add( getJsonSerializerFromType( argType, subtype ) );
            }
            ImmutableList<JSerializerType> parametersSerializer = parametersSerializerBuilder.build();

            builder.parameters( parametersSerializer );
            builder.beanMapper( true );
            builder.instance( constructorCallCode( ClassName.get( mapperInfo.getPackageName(), mapperInfo
                    .getSimpleSerializerClassName() ), parametersSerializer ) );
            return builder.build();
        }

        String message = "Type '" + type.getQualifiedSourceName() + "' is not supported";
        logger.log( TreeLogger.Type.WARN, message );
        throw new UnsupportedTypeException( message );
    }

    /**
     * Build the string that instantiate a {@link KeySerializer} for the given type.
     *
     * @param type type
     *
     * @return the code instantiating the {@link KeySerializer}.
     */
    protected JSerializerType getKeySerializerFromType( JType type ) throws UnsupportedTypeException {
        JSerializerType.Builder builder = new JSerializerType.Builder().type( type );
        if ( null != type.isWildcard() ) {
            // we use the base type to find the serializer to use
            type = type.isWildcard().getBaseType();
        }

        Optional<MapperInstance> keySerializer = configuration.getKeySerializer( type );
        if ( keySerializer.isPresent() ) {
            builder.instance( methodCallCode( keySerializer.get() ) );
            return builder.build();
        }

        JEnumType enumType = type.isEnum();
        if ( null != enumType ) {
            builder.instance( CodeBlock.builder()
                    .add( "$T.<$T<$T>>getInstance()", EnumKeySerializer.class, EnumKeySerializer.class, JClassName.get( enumType ) )
                    .build() );
            return builder.build();
        }

        if ( Enum.class.getName().equals( type.getQualifiedSourceName() ) ) {
            return builder.instance( CodeBlock.builder().add( "$T.getInstance()", EnumKeySerializer.class ).build() ).build();
        }

        String message = "Type '" + type.getQualifiedSourceName() + "' is not supported as map's key";
        logger.log( TreeLogger.Type.WARN, message );
        throw new UnsupportedTypeException( message );
    }

    /**
     * Build the string that instantiate a {@link JsonDeserializer} for the given type. If the type is a bean,
     * the implementation of {@link AbstractBeanJsonDeserializer} will
     * be created.
     *
     * @param type type
     *
     * @return the code instantiating the deserializer. Examples:
     * <ul>
     * <li>ctx.getIntegerDeserializer()</li>
     * <li>new org .PersonBeanJsonDeserializer()</li>
     * </ul>
     */
    protected JDeserializerType getJsonDeserializerFromType( JType type ) throws UnableToCompleteException, UnsupportedTypeException {
        return getJsonDeserializerFromType( type, false );
    }

    /**
     * Build the string that instantiate a {@link JsonDeserializer} for the given type. If the type is a bean,
     * the implementation of {@link AbstractBeanJsonDeserializer} will
     * be created.
     *
     * @param type type
     * @param subtype true if the deserializer is for a subtype
     *
     * @return the code instantiating the deserializer. Examples:
     * <ul>
     * <li>ctx.getIntegerDeserializer()</li>
     * <li>new org .PersonBeanJsonDeserializer()</li>
     * </ul>
     */
    protected JDeserializerType getJsonDeserializerFromType( JType type, boolean subtype ) throws UnableToCompleteException,
            UnsupportedTypeException {
        JDeserializerType.Builder builder = new JDeserializerType.Builder().type( type );
        if ( null != type.isWildcard() ) {
            // we use the base type to find the deserializer to use
            type = type.isWildcard().getBaseType();
        }

        if ( null != type.isRawType() ) {
            type = type.isRawType().getBaseType();
        }

        JTypeParameter typeParameter = type.isTypeParameter();
        if ( null != typeParameter ) {
            if ( !subtype || typeParameter.getDeclaringClass() == getMapperInfo().get().getType() ) {
                return builder.instance( CodeBlock.builder().add( String.format( TYPE_PARAMETER_DESERIALIZER_FIELD_NAME, typeParameter
                        .getOrdinal() ) )
                        .build() ).build();
            } else {
                type = typeParameter.getBaseType();
            }
        }

        Optional<MapperInstance> configuredDeserializer = configuration.getDeserializer( type );
        if ( configuredDeserializer.isPresent() ) {
            if ( null != type.isParameterized() || null != type.isGenericType() ) {
                JClassType[] typeArgs;
                if ( null != type.isGenericType() ) {
                    typeArgs = type.isGenericType().asParameterizedByWildcards().getTypeArgs();
                } else {
                    typeArgs = type.isParameterized().getTypeArgs();
                }

                ImmutableList.Builder<JDeserializerType> parametersDeserializerBuilder = ImmutableList.builder();
                for ( int i = 0; i < typeArgs.length; i++ ) {
                    JDeserializerType parameterDeserializerType;
                    if ( MapperType.KEY_DESERIALIZER == configuredDeserializer.get().getParameters()[i] ) {
                        parameterDeserializerType = getKeyDeserializerFromType( typeArgs[i] );
                    } else {
                        parameterDeserializerType = getJsonDeserializerFromType( typeArgs[i], subtype );
                    }
                    parametersDeserializerBuilder.add( parameterDeserializerType );
                }
                ImmutableList<JDeserializerType> parametersDeserializer = parametersDeserializerBuilder.build();
                builder.parameters( parametersDeserializer );
                builder.instance( methodCallCode( configuredDeserializer.get(), parametersDeserializer ) );

            } else {
                builder.instance( methodCallCode( configuredDeserializer.get() ) );
            }
            return builder.build();
        }

        if ( typeOracle.isJavaScriptObject( type ) ) {
            // it's a JSO and the user didn't give a custom deserializer. We use the default one.
            configuredDeserializer = configuration.getDeserializer( typeOracle.getJavaScriptObject() );
            return builder.instance( methodCallCode( configuredDeserializer.get() ) ).build();
        }

        JEnumType enumType = type.isEnum();
        if ( null != enumType ) {
            return builder.instance( CodeBlock.builder().add( "$T.newInstance($T.class)", EnumJsonDeserializer.class, JClassName
                    .get( enumType ) ).build() ).build();
        }

        if ( Enum.class.getName().equals( type.getQualifiedSourceName() ) ) {
            String message = "Type java.lang.Enum is not supported by deserialization";
            logger.log( TreeLogger.Type.WARN, message );
            throw new UnsupportedTypeException( message );
        }

        JArrayType arrayType = type.isArray();
        if ( null != arrayType ) {
            TypeSpec arrayCreator;
            Class arrayDeserializer;
            JType leafType = arrayType.getLeafType();

            if ( arrayType.getRank() == 1 ) {
                // one dimension array
                arrayCreator = TypeSpec.anonymousClassBuilder( "" )
                        .addSuperinterface( ParameterizedTypeName.get( ClassName.get( ArrayCreator.class ), JClassName.get( leafType ) ) )
                        .addMethod( MethodSpec.methodBuilder( "create" )
                                .addAnnotation( Override.class )
                                .addModifiers( Modifier.PUBLIC )
                                .addParameter( int.class, "length" )
                                .addStatement( "return new $T[$N]", JClassName.getRaw( leafType ), "length" )
                                .returns( JClassName.get( arrayType ) )
                                .build() )
                        .build();
                arrayDeserializer = ArrayJsonDeserializer.class;

            } else if ( arrayType.getRank() == 2 ) {
                // 2 dimensions array
                arrayCreator = TypeSpec.anonymousClassBuilder( "" )
                        .addSuperinterface( ParameterizedTypeName.get( ClassName.get( Array2dCreator.class ), JClassName.get( leafType ) ) )
                        .addMethod( MethodSpec.methodBuilder( "create" )
                                .addAnnotation( Override.class )
                                .addModifiers( Modifier.PUBLIC )
                                .addParameter( int.class, "first" )
                                .addParameter( int.class, "second" )
                                .addStatement( "return new $T[$N][$N]", JClassName.getRaw( leafType ), "first", "second" )
                                .returns( JClassName.get( arrayType ) )
                                .build() )
                        .build();
                arrayDeserializer = Array2dJsonDeserializer.class;

            } else {
                // more dimensions are not supported
                String message = "Arrays with 3 or more dimensions are not supported";
                logger.log( TreeLogger.Type.WARN, message );
                throw new UnsupportedTypeException( message );
            }

            JDeserializerType parameterDeserializerType = getJsonDeserializerFromType( leafType, subtype );
            builder.parameters( ImmutableList.of( parameterDeserializerType ) );
            builder.instance( CodeBlock.builder().add( "$T.newInstance($L, $L)", arrayDeserializer, parameterDeserializerType
                    .getInstance(), arrayCreator ).build() );
            return builder.build();
        }

        if ( null != type.isAnnotation() ) {
            String message = "Annotations are not supported";
            logger.log( TreeLogger.Type.WARN, message );
            throw new UnsupportedTypeException( message );
        }

        JClassType classType = type.isClassOrInterface();
        if ( null != classType ) {
            // it's a bean
            JClassType baseClassType = classType;
            JParameterizedType parameterizedType = classType.isParameterized();
            if ( null != parameterizedType ) {
                // it's a bean with generics, we create a deserializer based on generic type
                baseClassType = parameterizedType.getBaseType();
            }

            BeanJsonDeserializerCreator beanJsonDeserializerCreator = new BeanJsonDeserializerCreator( logger
                    .branch( Type.DEBUG, "Creating deserializer for " + baseClassType
                            .getQualifiedSourceName() ), context, configuration, typeOracle, baseClassType );
            BeanJsonMapperInfo mapperInfo = beanJsonDeserializerCreator.create();

            ImmutableList<? extends JType> typeParameters = getTypeParameters( classType, subtype );
            ImmutableList.Builder<JDeserializerType> parametersDeserializerBuilder = ImmutableList.builder();
            for ( JType argType : typeParameters ) {
                parametersDeserializerBuilder.add( getJsonDeserializerFromType( argType, subtype ) );
            }
            ImmutableList<JDeserializerType> parametersDeserializer = parametersDeserializerBuilder.build();

            builder.parameters( parametersDeserializer );
            builder.beanMapper( true );
            builder.instance( constructorCallCode( ClassName.get( mapperInfo.getPackageName(), mapperInfo
                    .getSimpleDeserializerClassName() ), parametersDeserializer ) );
            return builder.build();
        }

        String message = "Type '" + type.getQualifiedSourceName() + "' is not supported";
        logger.log( TreeLogger.Type.WARN, message );
        throw new UnsupportedTypeException( message );
    }

    /**
     * Build the string that instantiate a {@link KeyDeserializer} for the given type.
     *
     * @param type type
     *
     * @return the code instantiating the {@link KeyDeserializer}.
     */
    protected JDeserializerType getKeyDeserializerFromType( JType type ) throws UnsupportedTypeException {
        JDeserializerType.Builder builder = new JDeserializerType.Builder().type( type );
        if ( null != type.isWildcard() ) {
            // we use the base type to find the serializer to use
            type = type.isWildcard().getBaseType();
        }

        Optional<MapperInstance> keyDeserializer = configuration.getKeyDeserializer( type );
        if ( keyDeserializer.isPresent() ) {
            builder.instance( methodCallCode( keyDeserializer.get() ) );
            return builder.build();
        }

        JEnumType enumType = type.isEnum();
        if ( null != enumType ) {
            builder.instance( CodeBlock.builder().add( "$T.newInstance($T.class)", EnumKeyDeserializer.class, JClassName.get( enumType ) )
                    .build() );
            return builder.build();
        }

        String message = "Type '" + type.getQualifiedSourceName() + "' is not supported as map's key";
        logger.log( TreeLogger.Type.WARN, message );
        throw new UnsupportedTypeException( message );
    }

    private ImmutableList<? extends JType> getTypeParameters( JClassType classType, boolean subtype ) {
        JParameterizedType parameterizedType = classType.isParameterized();
        if ( null != parameterizedType ) {
            return ImmutableList.copyOf( parameterizedType.getTypeArgs() );
        }

        JGenericType genericType = classType.isGenericType();
        if ( null != genericType ) {
            if ( subtype ) {
                // if it's a subtype we look for parent in hierarchy equals to mapped class
                JClassType mappedClassType = getMapperInfo().get().getType();
                JClassType parentClassType = null;
                for ( JClassType parent : genericType.getFlattenedSupertypeHierarchy() ) {
                    if ( parent.getQualifiedSourceName().equals( mappedClassType.getQualifiedSourceName() ) ) {
                        parentClassType = parent;
                        break;
                    }
                }

                ImmutableList.Builder<JType> builder = ImmutableList.builder();
                for ( JTypeParameter typeParameter : genericType.getTypeParameters() ) {
                    JType arg = null;
                    if ( null != parentClassType && null != parentClassType.isParameterized() ) {
                        int i = 0;
                        for ( JClassType parentTypeParameter : parentClassType.isParameterized().getTypeArgs() ) {
                            if ( null != parentTypeParameter.isTypeParameter() && parentTypeParameter.isTypeParameter().getName()
                                    .equals( typeParameter.getName() ) ) {
                                if ( null != mappedClassType.isGenericType() ) {
                                    arg = mappedClassType.isGenericType().getTypeParameters()[i];
                                } else {
                                    arg = mappedClassType.isParameterized().getTypeArgs()[i];
                                }
                                break;
                            }
                            i++;
                        }
                    }
                    if ( null == arg ) {
                        arg = typeParameter.getBaseType();
                    }
                    builder.add( arg );
                }
                return builder.build();
            } else {
                ImmutableList.Builder<JType> builder = ImmutableList.builder();
                for ( JTypeParameter typeParameter : genericType.getTypeParameters() ) {
                    builder.add( typeParameter.getBaseType() );
                }
                return builder.build();
            }
        }

        return ImmutableList.of();
    }

    private CodeBlock constructorCallCode( ClassName className, ImmutableList<? extends JMapperType> parameters ) {
        CodeBlock.Builder builder = CodeBlock.builder();
        builder.add( "new $T", className );
        return methodCallCode( builder, parameters );
    }

    private CodeBlock methodCallCode( MapperInstance instance ) {
        return methodCallCode( instance, ImmutableList.<JMapperType>of() );
    }

    private CodeBlock methodCallCode( MapperInstance instance, ImmutableList<? extends JMapperType> parameters ) {
        CodeBlock.Builder builder = CodeBlock.builder();
        if ( null == instance.getInstanceCreationMethod().isConstructor() ) {
            builder.add( "$T.$L", JClassName.get( instance.getMapperType() ), instance.getInstanceCreationMethod().getName() );
        } else {
            builder.add( "new $T", JClassName.get( instance.getMapperType() ) );
        }
        return methodCallCode( builder, parameters );
    }

    private CodeBlock methodCallCode( CodeBlock.Builder builder, ImmutableList<? extends JMapperType> parameters ) {
        if ( parameters.isEmpty() ) {
            return builder.add( "()" ).build();
        }

        builder.add( "(" );

        Iterator<? extends JMapperType> iterator = parameters.iterator();
        builder.add( iterator.next().getInstance() );

        while ( iterator.hasNext() ) {
            builder.add( ", " );
            builder.add( iterator.next().getInstance() );
        }

        builder.add( ")" );
        return builder.build();
    }

}
