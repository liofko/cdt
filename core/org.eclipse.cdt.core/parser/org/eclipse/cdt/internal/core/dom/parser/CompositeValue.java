/*******************************************************************************
* Copyright (c) 2016 Institute for Software, HSR Hochschule fuer Technik
* Rapperswil, University of applied sciences and others
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*******************************************************************************/
package org.eclipse.cdt.internal.core.dom.parser;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IArrayType;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.ICompositeType;
import org.eclipse.cdt.core.dom.ast.IField;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.IValue;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPBase;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPField;
import org.eclipse.cdt.core.parser.util.CharArrayUtils;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTFieldReference;
import org.eclipse.cdt.internal.core.dom.parser.cpp.ClassTypeHelper;
import org.eclipse.cdt.internal.core.dom.parser.cpp.ICPPEvaluation;
import org.eclipse.cdt.internal.core.dom.parser.cpp.semantics.ActivationRecord;
import org.eclipse.cdt.internal.core.dom.parser.cpp.semantics.EvalFixed;
import org.eclipse.cdt.internal.core.dom.parser.cpp.semantics.EvalInitList;
import org.eclipse.cdt.internal.core.dom.parser.cpp.semantics.EvalUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

public final class CompositeValue implements IValue {
	private final ICPPEvaluation evaluation;
	private final ICPPEvaluation[] values;

	public CompositeValue(ICPPEvaluation evaluation, ICPPEvaluation[] values) {
		this.evaluation = evaluation;
		for (int i = 0; i < values.length; i++) {
			if (values[i] == null)
				values[i] = EvalFixed.INCOMPLETE;
		}
		this.values = values;
	}

	@Override
	public Long numericalValue() {
		return null;
	}

	@Override
	public Number numberValue() {
		return null;
	}

	@Override
	public ICPPEvaluation getEvaluation() {
		return evaluation;
	}

	@Override
	public char[] getSignature() {
		if (evaluation != null) {
			return evaluation.getSignature();
		}
		return new char[]{};
	}

	@Deprecated
	@Override
	public char[] getInternalExpression() {
		return CharArrayUtils.EMPTY_CHAR_ARRAY;
	}

	@Deprecated
	@Override
	public IBinding[] getUnknownBindings() {
		return IBinding.EMPTY_BINDING_ARRAY;
	}

	@Override
	public int numberOfSubValues() {
		return values.length;
	}

	@Override
	public ICPPEvaluation getSubValue(final int index) {
		return rangeIsValid(index) ? values[index] : EvalFixed.INCOMPLETE;
	}

	private boolean rangeIsValid(int index) {
		return 0 <= index && index < values.length;
	}

	public static IValue create(EvalInitList initList, IASTNode point) {
		ICPPEvaluation[] clauses = initList.getClauses();
		ICPPEvaluation[] values = new ICPPEvaluation[clauses.length];
		for (int i = 0; i < clauses.length; i++) {
			ICPPEvaluation eval = clauses[i];
			values[i] = new EvalFixed(eval.getType(point), eval.getValueCategory(point), eval.getValue(point));
		}
		return new CompositeValue(initList, values);
	}

	/**
	 * Creates a value representing an instance of the given array type initialized with
	 * the elements of the given initializer list.
	 */
	public static IValue create(EvalInitList initList, IArrayType type, IASTNode point) {
		Number arraySize = type.getSize().numberValue();
		if (arraySize == null) {
			// Array size is dependent. TODO: Handle this?
			return IntegralValue.UNKNOWN;
		}
		// More initializers than array members
		if (arraySize.intValue() < initList.getClauses().length) {
			return IntegralValue.ERROR;
		}
		IType elementType = type.getType();
		ICPPEvaluation[] values = new ICPPEvaluation[arraySize.intValue()];
		for (int i = 0; i < initList.getClauses().length; i++) {
			ICPPEvaluation eval = initList.getClauses()[i];
			IValue value = getValue(elementType, eval, point);
			values[i] = new EvalFixed(elementType, eval.getValueCategory(point), value);
		}
		return new CompositeValue(initList, values);
	}

	/**
	 * Gets the value of an evaluation, interpreted as a value of the given type.
	 */
	private static IValue getValue(IType type, ICPPEvaluation eval, IASTNode point) {
		IValue value;
		if (type instanceof IArrayType && eval instanceof EvalInitList) {
			value = CompositeValue.create((EvalInitList) eval, (IArrayType) type, point);
		} else if (type instanceof ICompositeType && eval instanceof EvalInitList) {
			value = CompositeValue.create((EvalInitList) eval, (ICompositeType) type, point);
		} else if (eval instanceof EvalInitList) {
			value = IntegralValue.UNKNOWN;
		} else {
			value = eval.getValue(null);
		}
		return value;
	}

	/**
	 * Creates a value representing an instance of the given composite type initialized with
	 * the elements of the given initializer list.
	 */
	public static IValue create(EvalInitList initList, ICompositeType type, IASTNode point) {
		IField[] fields;
		if (type instanceof ICPPClassType) {
			fields = ClassTypeHelper.getFields((ICPPClassType) type, point);
		} else {
			fields = type.getFields();
		}
		ICPPEvaluation[] values = new ICPPEvaluation[fields.length];
		ICPPEvaluation[] clauses = initList.getClauses();
		for (int i = 0; i < fields.length; i++) {
			if (i == clauses.length)
				break;
			IField field = fields[i];
			ICPPEvaluation eval = clauses[i];
			IType fieldType = field.getType();
			IValue value = getValue(fieldType, eval, point);
			values[i] = new EvalFixed(fieldType, eval.getValueCategory(null), value);
		}
		return new CompositeValue(initList, values);
	}

	// The set of class types for which composite value creation is in progress on each thread.
	// Used to guard against infinite recursion due to a class (invalidly) aggregating itself.
	private static final ThreadLocal<Set<ICPPClassType>> fCreateInProgress =
			new ThreadLocal<Set<ICPPClassType>>() {
		@Override
		protected Set<ICPPClassType> initialValue() {
			return new HashSet<>();
		}
	};

	/**
	 * Creates a value representing an instance of a class type, with the values of the fields
	 * determined by the default member initializers only. Constructors are not considered
	 * when determining the values of the fields.
	 */
	public static CompositeValue create(ICPPClassType classType) {
		Set<ICPPClassType> recursionProtectionSet = fCreateInProgress.get();
		if (!recursionProtectionSet.add(classType)) {
			return new CompositeValue(null, ICPPEvaluation.EMPTY_ARRAY);
		}
		try {
			ActivationRecord record = new ActivationRecord();
			ICPPEvaluation[] values = new ICPPEvaluation[ClassTypeHelper.getFields(classType, null).length];

			// Recursively create all the base class member variables.
			ICPPBase[] bases = ClassTypeHelper.getBases(classType, null);
			for (ICPPBase base : bases) {
				IBinding baseClass = base.getBaseClass();
				if (baseClass instanceof ICPPClassType) {
					ICPPClassType baseClassType = (ICPPClassType) baseClass;
					ICPPField[] baseFields = ClassTypeHelper.getDeclaredFields(baseClassType, null);
					IValue compValue = CompositeValue.create(baseClassType);
					for (ICPPField baseField : baseFields) {
						int fieldPos = CPPASTFieldReference.getFieldPosition(baseField);
						record.update(baseField, compValue.getSubValue(fieldPos));
						// TODO(nathanridge): This won't work with multiple inheritance, since 'fieldPos'
						// is a field position in the base class' hierarchy, while values[] expects
						// as index a field position in classType's hierarchy.
						values[fieldPos] = compValue.getSubValue(fieldPos);
					}
				}
			}

			ICPPField[] fields = ClassTypeHelper.getDeclaredFields(classType, null);
			for (ICPPField field : fields) {
				if (field.isStatic())
					continue;
				final ICPPEvaluation value = EvalUtil.getVariableValue(field, record);
				int fieldPos = CPPASTFieldReference.getFieldPosition(field);
				record.update(field, value);
				values[fieldPos] = value;
			}
			return new CompositeValue(null, values);
		} finally {
			recursionProtectionSet.remove(classType);
		}
	}

	@Override
	public ICPPEvaluation[] getAllSubValues() {
		return values;
	}

	@Override
	public void setSubValue(int position, ICPPEvaluation newValue) {
		if (position >= 0 && position < values.length) {
			values[position] = newValue == null ? EvalFixed.INCOMPLETE : newValue;
		} else {
			CCorePlugin.log(IStatus.WARNING, "Out-of-bounds access to composite value: " + position + //$NON-NLS-1$ 
					" (length is " + values.length + ")");  //$NON-NLS-1$//$NON-NLS-2$
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append('[');
		for (int i = 0; i < values.length; i++) {
			if (i != 0) {
				builder.append(',').append(' ');
			}
			builder.append(values[i].toString());
		}
		builder.append(']');
		return builder.toString();
	}

	@Override
	public IValue clone() {
		ICPPEvaluation[] newValues = new ICPPEvaluation[values.length];
		for (int i = 0; i < newValues.length; i++) {
			ICPPEvaluation eval = values[i];
			if (eval == EvalFixed.INCOMPLETE) {
				newValues[i] = eval;
			} else {
				IValue newValue = eval.getValue(null).clone();
				newValues[i] = new EvalFixed(eval.getType(null), eval.getValueCategory(null), newValue);
			}
		}
		return new CompositeValue(evaluation, newValues);
	}

	@Override
	public void marshal(ITypeMarshalBuffer buf) throws CoreException {
		buf.putShort(ITypeMarshalBuffer.COMPOSITE_VALUE);
		buf.marshalEvaluation(evaluation, true);
		buf.putInt(values.length);
		for (ICPPEvaluation value : values) {
			buf.marshalEvaluation(value, true);
		}
	}

	public static IValue unmarshal(short firstBytes, ITypeMarshalBuffer buf) throws CoreException {
		ICPPEvaluation evaluation = buf.unmarshalEvaluation();
		int len = buf.getInt();
		ICPPEvaluation values[] = new ICPPEvaluation[len];
		for (int i = 0; i < len; i++) {
			values[i] = buf.unmarshalEvaluation();
		}
		return new CompositeValue(evaluation, values);
	}
}
