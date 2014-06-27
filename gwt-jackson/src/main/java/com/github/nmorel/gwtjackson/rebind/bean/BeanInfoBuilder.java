/*
 * Copyright 2014 Nicolas Morel
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

package com.github.nmorel.gwtjackson.rebind.bean;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.google.gwt.core.ext.typeinfo.JAbstractMethod;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JParameter;
import com.google.gwt.thirdparty.guava.common.base.Optional;

/**
 * @author Nicolas Morel
 */
final class BeanInfoBuilder {

    private JClassType type;

    private List<JClassType> parameterizedTypes = Collections.emptyList();

    private Optional<JAbstractMethod> creatorMethod = Optional.absent();

    private Map<String, JParameter> creatorParameters = Collections.emptyMap();

    private boolean creatorDefaultConstructor;

    private boolean creatorDelegation;

    private Optional<BeanTypeInfo> typeInfo = Optional.absent();

    private Set<String> ignoredFields = Collections.emptySet();

    private JsonAutoDetect.Visibility fieldVisibility = JsonAutoDetect.Visibility.DEFAULT;

    private JsonAutoDetect.Visibility getterVisibility = JsonAutoDetect.Visibility.DEFAULT;

    private JsonAutoDetect.Visibility isGetterVisibility = JsonAutoDetect.Visibility.DEFAULT;

    private JsonAutoDetect.Visibility setterVisibility = JsonAutoDetect.Visibility.DEFAULT;

    private JsonAutoDetect.Visibility creatorVisibility = JsonAutoDetect.Visibility.DEFAULT;

    private boolean ignoreUnknown;

    private List<String> propertyOrderList = Collections.emptyList();

    private boolean propertyOrderAlphabetic;

    private Optional<BeanIdentityInfo> identityInfo = Optional.absent();

    BeanInfoBuilder() {
    }

    void setType( JClassType type ) {
        this.type = type;
    }

    void setParameterizedTypes( List<JClassType> parameterizedTypes ) {
        this.parameterizedTypes = parameterizedTypes;
    }

    void setCreatorMethod( Optional<JAbstractMethod> creatorMethod ) {
        this.creatorMethod = creatorMethod;
    }

    public Map<String, JParameter> getCreatorParameters() {
        return creatorParameters;
    }

    void setCreatorParameters( Map<String, JParameter> creatorParameters ) {
        this.creatorParameters = creatorParameters;
    }

    void setCreatorDefaultConstructor( boolean creatorDefaultConstructor ) {
        this.creatorDefaultConstructor = creatorDefaultConstructor;
    }

    void setCreatorDelegation( boolean creatorDelegation ) {
        this.creatorDelegation = creatorDelegation;
    }

    void setTypeInfo( Optional<BeanTypeInfo> typeInfo ) {
        this.typeInfo = typeInfo;
    }

    void setIgnoredFields( Set<String> ignoredFields ) {
        this.ignoredFields = ignoredFields;
    }

    void setFieldVisibility( Visibility fieldVisibility ) {
        this.fieldVisibility = fieldVisibility;
    }

    void setGetterVisibility( Visibility getterVisibility ) {
        this.getterVisibility = getterVisibility;
    }

    void setIsGetterVisibility( Visibility isGetterVisibility ) {
        this.isGetterVisibility = isGetterVisibility;
    }

    void setSetterVisibility( Visibility setterVisibility ) {
        this.setterVisibility = setterVisibility;
    }

    void setCreatorVisibility( Visibility creatorVisibility ) {
        this.creatorVisibility = creatorVisibility;
    }

    void setIgnoreUnknown( boolean ignoreUnknown ) {
        this.ignoreUnknown = ignoreUnknown;
    }

    void setPropertyOrderList( List<String> propertyOrderList ) {
        this.propertyOrderList = propertyOrderList;
    }

    public boolean isPropertyOrderAlphabetic() {
        return propertyOrderAlphabetic;
    }

    void setPropertyOrderAlphabetic( boolean propertyOrderAlphabetic ) {
        this.propertyOrderAlphabetic = propertyOrderAlphabetic;
    }

    void setIdentityInfo( Optional<BeanIdentityInfo> identityInfo ) {
        this.identityInfo = identityInfo;
    }

    BeanInfo build() {
        return new ImmutableBeanInfo( type, parameterizedTypes, creatorMethod, creatorParameters, creatorDefaultConstructor,
                creatorDelegation, typeInfo, ignoredFields, fieldVisibility, getterVisibility, isGetterVisibility, setterVisibility,
                creatorVisibility, ignoreUnknown, propertyOrderList, propertyOrderAlphabetic, identityInfo );
    }
}
