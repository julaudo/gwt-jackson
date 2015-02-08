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

package com.github.nmorel.gwtjackson.rebind.writer;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.core.ext.typeinfo.JArrayType;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JParameterizedType;
import com.google.gwt.core.ext.typeinfo.JPrimitiveType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.core.ext.typeinfo.JTypeParameter;
import com.google.gwt.core.ext.typeinfo.JWildcardType;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

/**
 * @author Nicolas Morel
 */
public final class JClassName {

    private JClassName() {}

    public static TypeName get( JType type ) {
        return get( false, type );
    }

    public static TypeName get( boolean boxed, JType type ) {
        if ( null != type.isPrimitive() ) {
            return getPrimitiveName( type.isPrimitive(), boxed );
        } else if ( null != type.isParameterized() ) {
            return getParameterizedTypeName( type.isParameterized() );
        } else if ( null != type.isArray() ) {
            return getArrayTypeName( type.isArray() );
        } else if ( null != type.isTypeParameter() ) {
            return getTypeVariableName( type.isTypeParameter() );
        } else if ( null != type.isWildcard() ) {
            return getWildcardTypeName( type.isWildcard() );
        } else {
            return getClassName( type.isClassOrInterface() );
        }
    }

    public static ParameterizedTypeName get( Class clazz, JType... types ) {
        return ParameterizedTypeName.get( ClassName.get( clazz ), get( true, types ) );
    }

    public static TypeName getRaw( JType type ) {
        if ( null != type.isPrimitive() ) {
            return getPrimitiveName( type.isPrimitive(), false );
        } else if ( null != type.isParameterized() ) {
            return getClassName( type.isParameterized().getRawType() );
        } else if ( null != type.isGenericType() ) {
            return getClassName( type.isGenericType().getRawType() );
        } else if ( null != type.isArray() ) {
            return getArrayTypeName( type.isArray() );
        } else {
            return getClassName( type.isClassOrInterface() );
        }
    }

    public static TypeName[] get( JType... types ) {
        return get( false, types );
    }

    private static TypeName[] get( boolean boxed, JType... types ) {
        TypeName[] result = new TypeName[types.length];
        for ( int i = 0; i < types.length; i++ ) {
            result[i] = get( boxed, types[i] );
        }
        return result;
    }

    private static TypeName getPrimitiveName( JPrimitiveType type, boolean boxed ) {
        if ( "boolean".equals( type.getSimpleSourceName() ) ) {
            return boxed ? ClassName.get( Boolean.class ) : TypeName.BOOLEAN;
        } else if ( "byte".equals( type.getSimpleSourceName() ) ) {
            return boxed ? ClassName.get( Byte.class ) : TypeName.BYTE;
        } else if ( "short".equals( type.getSimpleSourceName() ) ) {
            return boxed ? ClassName.get( Short.class ) : TypeName.SHORT;
        } else if ( "int".equals( type.getSimpleSourceName() ) ) {
            return boxed ? ClassName.get( Integer.class ) : TypeName.INT;
        } else if ( "long".equals( type.getSimpleSourceName() ) ) {
            return boxed ? ClassName.get( Long.class ) : TypeName.LONG;
        } else if ( "char".equals( type.getSimpleSourceName() ) ) {
            return boxed ? ClassName.get( Character.class ) : TypeName.CHAR;
        } else if ( "float".equals( type.getSimpleSourceName() ) ) {
            return boxed ? ClassName.get( Float.class ) : TypeName.FLOAT;
        } else if ( "double".equals( type.getSimpleSourceName() ) ) {
            return boxed ? ClassName.get( Double.class ) : TypeName.DOUBLE;
        } else {
            return boxed ? ClassName.get( Void.class ) : TypeName.VOID;
        }
    }

    private static ParameterizedTypeName getParameterizedTypeName( JParameterizedType type ) {
        return ParameterizedTypeName.get( getClassName( type ), get( true, type.getTypeArgs() ) );
    }

    private static ArrayTypeName getArrayTypeName( JArrayType type ) {
        return ArrayTypeName.of( get( type.getComponentType() ) );
    }

    public static TypeVariableName getTypeVariableName( JTypeParameter type ) {
        return TypeVariableName.get( type.getName(), get( type.getBounds() ) );
    }

    private static WildcardTypeName getWildcardTypeName( JWildcardType type ) {
        switch ( type.getBoundType() ) {
            case SUPER:
                return WildcardTypeName.supertypeOf( get( type.getFirstBound() ) );
            default:
                return WildcardTypeName.subtypeOf( get( type.getFirstBound() ) );
        }
    }

    private static ClassName getClassName( JClassType type ) {
        JClassType enclosingType = type.getEnclosingType();

        if ( null == enclosingType ) {
            return ClassName.get( type.getPackage()
                    .getName(), type.getSimpleSourceName() );
        }

        List<String> types = new ArrayList<String>();
        types.add( type.getSimpleSourceName() );
        while ( null != enclosingType ) {
            types.add( 0, enclosingType.getSimpleSourceName() );
            enclosingType = enclosingType.getEnclosingType();
        }

        String parentType = types.remove( 0 );
        String[] childs = types.toArray( new String[types.size()] );
        return ClassName.get( type.getPackage()
                .getName(), parentType, childs );
    }

}
