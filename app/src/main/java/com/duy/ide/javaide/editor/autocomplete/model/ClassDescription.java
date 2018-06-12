/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.duy.ide.javaide.editor.autocomplete.model;


import android.text.Editable;

import com.android.annotations.Nullable;
import com.duy.common.DLog;
import com.duy.ide.code.api.SuggestItem;
import com.duy.ide.editor.view.IEditAreaView;
import com.duy.ide.javaide.editor.autocomplete.dex.IClass;
import com.duy.ide.javaide.editor.autocomplete.dex.IField;
import com.duy.ide.javaide.editor.autocomplete.dex.IMethod;
import com.duy.ide.javaide.editor.autocomplete.dex.JavaClassReader;
import com.duy.ide.javaide.editor.autocomplete.internal.PackageImporter;
import com.duy.ide.javaide.editor.autocomplete.util.JavaUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

/**
 * Created by Duy on 20-Jul-17.
 */

public class ClassDescription extends JavaSuggestItemImpl implements IClass {
    private static final String TAG = "ClassDescription";
    private final String mClassName;
    private final ArrayList<ClassConstructorDescription> mConstructors = new ArrayList<>();
    private final ArrayList<FieldDescription> mFields = new ArrayList<>();
    private final ArrayList<MethodDescription> mMethods = new ArrayList<>();
    private final ArrayList<ClassDescription> mImplements = new ArrayList<>();
    @Nullable
    private final ClassDescription mSuperClass;
    private final int mModifiers;

    public ClassDescription(Class c) {
        mClassName = c.getName();
        if (c.getSuperclass() != null) {
            mSuperClass = JavaClassReader.getInstance().getClassWrapper(c.getSuperclass());
        } else {
            mSuperClass = null;
        }
        mModifiers = c.getModifiers();
    }

    @Override
    public String getName() {
        return getSimpleName() + " (" + getPackageName() + ")";
    }

    @Override
    public final String getDescription() {
        return getPackageName();
    }

    @Override
    public String getReturnType() {
        return "";
    }

    @Override
    public char getTypeHeader() {
        return 'c';
    }

    @Override
    public int getSuggestionPriority() {
        return JavaSuggestItemImpl.CLASS_DESC;
    }

    public void onSelectThis(IEditAreaView editorView) {
        try {
            final int length = getIncomplete().length();
            final int start = editorView.getSelectionStart() - length;

            Editable editable = editorView.getEditableText();
            editable.delete(start, editorView.getSelectionStart());
            editable.insert(start, getSimpleName());
            PackageImporter.importClass(editable, getFullClassName());

            if (DLog.DEBUG) DLog.d(TAG, "onSelectThis: import class " + this);
        } catch (Exception e) {
            if (DLog.DEBUG) DLog.e(TAG, "import class " + this, e);
        }
    }


    public String getSimpleName() {
        return JavaUtil.getSimpleName(mClassName);
    }


    public String getFullClassName() {
        return mClassName;
    }

    @Nullable
    public final ClassDescription getSuperclass() {
        return mSuperClass;
    }

    public final String getPackageName() {
        return JavaUtil.getPackageName(mClassName);
    }

    public ArrayList<ClassConstructorDescription> getConstructors() {
        return mConstructors;
    }

    public ArrayList<FieldDescription> getFields() {
        return mFields;
    }

    public void addConstructor(ClassConstructorDescription constructorDescription) {
        this.mConstructors.add(constructorDescription);
    }

    public void addField(FieldDescription fieldDescription) {
        mFields.add(fieldDescription);
    }

    public void addMethod(MethodDescription methodDescription) {
        mMethods.add(methodDescription);
    }

    public ArrayList<MethodDescription> getMethods() {
        return mMethods;
    }

    @Override
    public String toString() {
        return mClassName;
    }

    @SuppressWarnings("ConstantConditions")
    public ArrayList<SuggestItem> getMember(String prefix) {
        ArrayList<SuggestItem> result = new ArrayList<>();
        for (ClassConstructorDescription constructor : mConstructors) {
            if (!prefix.isEmpty()) {
                if (constructor.getName().startsWith(prefix)) {
                    result.add(constructor);
                }
            }
        }
        for (FieldDescription field : mFields) {
            if (prefix.isEmpty() || field.getName().startsWith(prefix)) {
                result.add(field);
            }
        }
        getMethods(result, prefix);
        return result;
    }

    public void getMethods(ArrayList<SuggestItem> result, String prefix) {
        for (MethodDescription method : mMethods) {
            if (prefix.isEmpty() || method.getName().startsWith(prefix)) {
                result.add(method);
            }
        }
    }


    @Override
    public int getModifiers() {
        return mModifiers;
    }

    @Override
    public boolean isInterface() {
        return false;
    }


    public boolean isEnum() {
        // An enum must both directly extend java.lang.Enum and have
        // the ENUM bit set; classes for specialized enum constants
        // don't do the former.
        if (getSuperclass() == null) {
            return false;
        }
        return this.getSuperclass().getFullClassName()
                .equals(java.lang.Enum.class.getName());
    }

    @Override
    public IMethod getMethod(String methodName, IClass[] argsType) {
        // TODO: 13-Jun-18 support types
        for (MethodDescription method : mMethods) {
            if (method.getMethodName().equals(methodName)) {
                return method;
            }
        }
        if (getSuperclass() != null) {
            return getSuperclass().getMethod(methodName, argsType);
        }
        return null;
    }

    @Override
    public IField getField(String name) {
        for (FieldDescription field : mFields) {
            if (field.getFieldName().equals(name)) {
                return field;
            }
        }
        return null;
    }

    public void initMembers(Class c) {
        for (Constructor constructor : c.getConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers())) {
                addConstructor(new ClassConstructorDescription(constructor));
            }
        }

        for (Field field : c.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())) {
                if (!field.getName().equals(field.getDeclaringClass().getName())) {
                    addField(new FieldDescription(field));
                }
            }
        }

        for (Method method : c.getMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                addMethod(new MethodDescription(method));
            }
        }
    }
}
