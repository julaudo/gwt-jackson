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

import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JParameterizedType;
import com.google.gwt.core.ext.typeinfo.JPrimitiveType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

/**
 * @author Nicolas Morel
 */
public final class JClassName {

    private JClassName() {}

    public static TypeName get( JType type ) {
        if ( null != type.isPrimitive() ) {
            return getPrimitiveName( type.isPrimitive() );
        } else if ( null != type.isParameterized() ) {
            return getParameterizedTypeName( type.isParameterized() );
        } else {
            return getClassName( type.isClassOrInterface() );
        }
    }

    public static TypeName[] get( JType[] types ) {
        TypeName[] result = new TypeName[types.length];
        for ( int i = 0; i < types.length; i++ ) {
            result[i] = get( types[i] );
        }
        return result;
    }

    private static TypeName getPrimitiveName( JPrimitiveType type ) {
        if ( "boolean".equals( type.getSimpleSourceName() ) ) {
            return TypeName.BOOLEAN;

        } else if ( "byte".equals( type.getSimpleSourceName() ) ) {
            return TypeName.BYTE;

        } else if ( "short".equals( type.getSimpleSourceName() ) ) {
            return TypeName.SHORT;

        } else if ( "int".equals( type.getSimpleSourceName() ) ) {
            return TypeName.INT;

        } else if ( "long".equals( type.getSimpleSourceName() ) ) {
            return TypeName.LONG;

        } else if ( "char".equals( type.getSimpleSourceName() ) ) {
            return TypeName.CHAR;

        } else if ( "float".equals( type.getSimpleSourceName() ) ) {
            return TypeName.FLOAT;

        } else if ( "double".equals( type.getSimpleSourceName() ) ) {
            return TypeName.DOUBLE;
        } else {
            return TypeName.VOID;
        }
    }

    private static ParameterizedTypeName getParameterizedTypeName( JParameterizedType type ) {
        return ParameterizedTypeName.get( getClassName( type ), get( type.getTypeArgs() ) );
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
