/*******************************************************************************
 * Copyright (c) 2004, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM - Initial API and implementation
 *    Markus Schorn (Wind River Systems)
 *    Bryan Wilkinson (QNX)
 *    Andrew Ferguson (Symbian)
 *    Sergey Prigogin (Google)
 *******************************************************************************/
package org.eclipse.cdt.internal.core.dom.parser.cpp.semantics;

import static org.eclipse.cdt.internal.core.dom.parser.cpp.semantics.CVQualifier._;
import static org.eclipse.cdt.internal.core.dom.parser.cpp.semantics.SemanticUtil.*;

import java.util.BitSet;

import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.IArrayType;
import org.eclipse.cdt.core.dom.ast.IBasicType;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IEnumeration;
import org.eclipse.cdt.core.dom.ast.IFunctionType;
import org.eclipse.cdt.core.dom.ast.IPointerType;
import org.eclipse.cdt.core.dom.ast.IProblemBinding;
import org.eclipse.cdt.core.dom.ast.IQualifierType;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.IValue;
import org.eclipse.cdt.core.dom.ast.IBasicType.Kind;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPBase;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPBasicType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPConstructor;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPFunctionType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPMethod;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPPointerToMemberType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPReferenceType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPSpecialization;
import org.eclipse.cdt.internal.core.dom.parser.ArithmeticConversion;
import org.eclipse.cdt.internal.core.dom.parser.Value;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPBasicType;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPPointerToMemberType;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPPointerType;
import org.eclipse.cdt.internal.core.dom.parser.cpp.ICPPDeferredClassInstance;
import org.eclipse.cdt.internal.core.dom.parser.cpp.semantics.Cost.Rank;
import org.eclipse.cdt.internal.core.dom.parser.cpp.semantics.Cost.ReferenceBinding;

/**
 * Routines for calculating the cost of conversions.
 */
public class Conversions {
	enum UDCMode {allowUDC, noUDC, deferUDC}

	private static final BitSet RVBITSET = new BitSet();
	private static final BitSet LVBITSET = new BitSet();
	static {
		LVBITSET.set(0, true);
	}
	/**
	 * Computes the cost of an implicit conversion sequence
	 * [over.best.ics] 13.3.3.1
	 * @param target the target (parameter) type
	 * @param exprType the source (argument) type
	 * @param exprIsLValue whether the source type is an lvalue
	 * @param isImpliedObjectType
	 * @return the cost of converting from source to target
	 * @throws DOMException
	 */
	public static Cost checkImplicitConversionSequence(IType target, IType exprType,
			boolean exprIsLValue, UDCMode udc, boolean isImpliedObjectType) throws DOMException {
		if (isImpliedObjectType) {
			udc= UDCMode.noUDC;
		}
		
		target= getNestedType(target, TDEF);
		exprType= getNestedType(exprType, TDEF | REF);
		
		if (target instanceof ICPPReferenceType) {
			// [8.5.3-5] initialization of a reference 
			final boolean isLValueRef= !((ICPPReferenceType) target).isRValueReference();
			final IType cv1T1= getNestedType(target, TDEF | REF);
			final IType T1= getNestedType(cv1T1, TDEF | REF | ALLCVQ);
			final IType cv2T2= exprType;
			final IType T2= getNestedType(cv2T2, TDEF | REF | ALLCVQ);

			final boolean isImplicitWithoutRefQualifier = isImpliedObjectType; // mstodo need to pass this information to here
			ReferenceBinding refBindingType= ReferenceBinding.OTHER;
			if (!isImplicitWithoutRefQualifier) {
				if (isLValueRef) {
					refBindingType= ReferenceBinding.LVALUE_REF;
				} else if (exprIsLValue) {
					refBindingType= ReferenceBinding.RVALUE_REF_BINDS_RVALUE;
				}
			}

			
			// If the reference is an lvalue reference and ...
			if (isLValueRef) {
				// ... the initializer expression is an lvalue (but is not a bit field)
				// [for overload resolution bit-fields are treated the same, error if selected as best match]
				if (exprIsLValue) {
					// ... and "cv1 T1" is reference-compatible with "cv2 T2" 
					Cost cost= isReferenceCompatible(cv1T1, cv2T2, isImpliedObjectType);
					if (cost != null) {
						// [13.3.3.1.4-1] direct binding has either identity or conversion rank.					
						if (cost.getInheritanceDistance() > 0) {
							cost.setRank(Rank.CONVERSION);
						} 
						cost.setReferenceBinding(refBindingType);
						return cost;
					} 
				}
				// ... or has a class type (i.e., T2 is a class type), where T1 is not reference-related to T2, and can be
				// implicitly converted to an lvalue of type cv3 T3, where cv1 T1 is reference-compatible with 
				// cv3 T3 (this conversion is selected by enumerating the applicable conversion functions (13.3.1.6)
				// and choosing the best one through overload resolution (13.3)),
				if (T2 instanceof ICPPClassType && udc != UDCMode.noUDC && isReferenceRelated(T1, T2) < 0) {
					Cost cost= conversionFuncForDirectReference(cv1T1, cv2T2, T2, true);
					if (cost != null) {
						cost.setReferenceBinding(refBindingType);
						return cost;
					}
				}
			}
			
			// Otherwise, the reference shall be an lvalue reference to a non-volatile const type (i.e., cv1
			// shall be const), or the reference shall be an rvalue reference and the initializer expression
			// shall be an rvalue.
			boolean ok;
			if (isLValueRef) {
				ok = getCVQualifier(cv1T1) == CVQualifier.c;
			} else {
				ok= !exprIsLValue;
			}
			if (!ok) {
				return new Cost(exprType, cv1T1, Rank.NO_MATCH);
			}

			// If T1 and T2 are class types and ...
			if (T1 instanceof ICPPClassType && T2 instanceof ICPPClassType) {
				// ... the initializer expression is an rvalue and cv1 T1 is reference-compatible with cv2 T2
				if (!exprIsLValue) {
					Cost cost= isReferenceCompatible(cv1T1, cv2T2, isImpliedObjectType);
					if (cost != null) {
						// [13.3.3.1.4-1] direct binding has either identity or conversion rank.					
						if (cost.getInheritanceDistance() > 0) {
							cost.setRank(Rank.CONVERSION);
						} 
						cost.setReferenceBinding(refBindingType);
						return cost;
					} 
				}

				// or T1 is not reference-related to T2 and the initializer expression can be implicitly
				// converted to an rvalue of type cv3 T3 (this conversion is selected by enumerating the
				// applicable conversion functions (13.3.1.6) and choosing the best one through overload
				// resolution (13.3)), then the reference is bound to the initializer expression rvalue in the
				// first case and to the object that is the result of the conversion in the second case (or,
				// in either case, to the appropriate base class subobject of the object).
				if (udc != UDCMode.noUDC && isReferenceRelated(T1, T2) < 0) {
					Cost cost= conversionFuncForDirectReference(cv1T1, cv2T2, T2, false);
					if (cost != null) {
						cost.setReferenceBinding(refBindingType);
						return cost;
					}
				}
			}
			
			// If the initializer expression is an rvalue, with T2 an array type, and cv1 T1 is
			// reference-compatible with cv2 T2, the reference is bound to the object represented by the
			// rvalue (see 3.10).
			if (!exprIsLValue && T2 instanceof IArrayType) {
				Cost cost= isReferenceCompatible(cv1T1, cv2T2, isImpliedObjectType);
				if (cost != null) {
					cost.setReferenceBinding(refBindingType);
					return cost;
				}
			}

			//  Otherwise, a temporary of type cv1 T1 is created and initialized from the initializer
			// expression using the rules for a non-reference copy initialization (8.5). The reference is then
			// bound to the temporary. If T1 is reference-related to T2, cv1 must be the same cv-qualification
			// as, or greater cv-qualification than, cv2; otherwise, the program is ill-formed.
				
			// 13.3.3.1.7 no temporary object when converting the implicit object parameter
			if (!isImpliedObjectType) {
				if (isReferenceRelated(T1, T2) < 0 || compareQualifications(cv1T1, cv2T2) >= 0) {
					Cost cost= nonReferenceConversion(exprIsLValue, cv2T2, T1, udc, false);
					if (!isImplicitWithoutRefQualifier) {
						cost.setReferenceBinding(isLValueRef ? ReferenceBinding.LVALUE_REF : ReferenceBinding.RVALUE_REF_BINDS_RVALUE);
					}
					return cost;
				}
			}
			return new Cost(exprType, cv1T1, Rank.NO_MATCH);
		} 
		
		// Non-reference binding
		IType uqsource= getNestedType(exprType, TDEF | REF | ALLCVQ);
		IType uqtarget= getNestedType(target, TDEF | REF | ALLCVQ);
		
		// [13.3.3.1-6] Derived to base conversion
		if (uqsource instanceof ICPPClassType && uqtarget instanceof ICPPClassType) {
			int depth= calculateInheritanceDepth(CPPSemantics.MAX_INHERITANCE_DEPTH, uqsource, uqtarget);
			if (depth > -1) {
				if (depth == 0) {
					return new Cost(uqsource, uqtarget, Rank.IDENTITY);
				}
				Cost cost= new Cost(uqsource, uqtarget, Rank.CONVERSION);
				cost.setInheritanceDistance(depth);
				return cost;
			}
		}
		
		return nonReferenceConversion(exprIsLValue, exprType, uqtarget, udc, isImpliedObjectType);
	}

	/**
	 * 0x: 13.3.1.6 Initialization by conversion function for direct reference binding	 
	 */
	private static Cost conversionFuncForDirectReference(final IType cv1T1, final IType cv2T2, final IType T2, boolean forLValue)
			throws DOMException {
		ICPPMethod[] fcns= SemanticUtil.getConversionOperators((ICPPClassType) T2);
		Cost operatorCost= null;
		FunctionCost bestUdcCost= null;
		boolean ambiguousConversionOperator= false;
		if (fcns.length > 0 && !(fcns[0] instanceof IProblemBinding)) {
			for (final ICPPMethod op : fcns) {
				final ICPPFunctionType ft = op.getType();
				IType convertedType= ft.getReturnType();
				final boolean isLValue = CPPVisitor.isLValueReference(convertedType);
				if (isLValue == forLValue) { // require an lvalue or rvalue
					IType implicitObjectType= CPPSemantics.getImplicitType(op, ft.isConst(), ft.isVolatile());
					Cost udcCost= isReferenceCompatible(getNestedType(implicitObjectType, TDEF | REF), cv2T2, true); // expression type to implicit object type
					if (udcCost != null) {
						FunctionCost udcFuncCost= new FunctionCost(op, udcCost);
						int cmp= udcFuncCost.compareTo(null, bestUdcCost);
						if (cmp <= 0) {
							Cost cost= isReferenceCompatible(cv1T1, getNestedType(convertedType, TDEF | REF), false); // converted to target
							if (cost != null) {
								bestUdcCost= udcFuncCost;
								ambiguousConversionOperator= cmp == 0;
								operatorCost= cost;
								operatorCost.setUserDefinedConversion(op);
							}
						}
					}
				}
			}
		}

		if (operatorCost != null && !ambiguousConversionOperator) {
			return operatorCost;
		}
		return null;
	}

	private static Cost nonReferenceConversion(boolean sourceIsLValue, IType source, IType target, UDCMode udc, boolean isImpliedObject) throws DOMException {
		// [13.3.3.1-6] Subsume cv-qualifications
		IType uqSource= SemanticUtil.getNestedType(source, TDEF | ALLCVQ);
		Cost cost= checkStandardConversionSequence(uqSource, sourceIsLValue, target, isImpliedObject);
		if (cost.getRank() != Rank.NO_MATCH || udc == UDCMode.noUDC) 
			return cost;
		
		Cost temp = checkUserDefinedConversionSequence(sourceIsLValue, source, target, udc == UDCMode.deferUDC);
		if (temp != null) {
			cost = temp;
		}
		return cost;
	}

	/**
	 * [3.9.3-4] Implements cv-ness (partial) comparison. There is a (partial)
	 * ordering on cv-qualifiers, so that a type can be said to be more
	 * cv-qualified than another.
	 * @return <ul>
	 * <li>3 if cv1 == const volatile cv2 
	 * <li>2 if cv1 == volatile cv2
	 * <li>1 if cv1 == const cv2
	 * <li>EQ 0 if cv1 == cv2
	 * <li>LT -1 if cv1 is less qualified than cv2 or not comparable
	 * </ul>
	 * @throws DOMException
	 */
	private static final int compareQualifications(IType t1, IType t2) throws DOMException {
		CVQualifier cv1= getCVQualifier(t1);
		CVQualifier cv2= getCVQualifier(t2);
		
		// same qualifications
		if (cv1 == cv2)
			return 0;

		switch (cv1) {
		case cv:
			switch (cv2) {
			case _: return 3;
			case c: return 2;
			case v: return 1;
			case cv: return 0;
			}
			break;
		case c:
			return cv2 == _ ? 1 : -1;
		case v:
			return cv2 == _ ? 2 : -1;
		case _:
			return -1;
		}
		return -1;
	}

	/**
	 * [8.5.3] "cv1 T1" is reference-related to "cv2 T2" if T1 is the same type as T2,
	 * or T1 is a base class of T2.
	 * Note this is not a symmetric relation.
	 * @return inheritance distance, or -1, if <code>cv1t1</code> is not reference-related to <code>cv2t2</code>
	 */
	private static final int isReferenceRelated(IType cv1Target, IType cv2Source) throws DOMException {
		IType t= SemanticUtil.getNestedType(cv1Target, TDEF | REF);
		IType s= SemanticUtil.getNestedType(cv2Source, TDEF | REF);
		
		// The way cv-qualification is currently modeled means
		// we must cope with IPointerType objects separately.
		if (t instanceof IPointerType) {
			if (s instanceof IPointerType) {
				t= SemanticUtil.getNestedType(((IPointerType) t).getType(), TDEF | REF);
				s= SemanticUtil.getNestedType(((IPointerType) s).getType(), TDEF | REF);
			} else {
				return -1;
			}
		} else if (t instanceof IArrayType) {
			if (s instanceof IArrayType) {
				final IArrayType at = (IArrayType) t;
				final IArrayType st = (IArrayType) s;
				final IValue av= at.getSize();
				final IValue sv= st.getSize();
				if (av == sv || (av != null && av.equals(sv))) {
					t= SemanticUtil.getNestedType(at.getType(), TDEF | REF);
					s= SemanticUtil.getNestedType(st.getType(), TDEF | REF);
				} else {
					return -1;
				}
			} else {
				return -1;
			}
		} else {
			if (t instanceof IQualifierType)
				t= SemanticUtil.getNestedType(((IQualifierType) t).getType(), TDEF | REF);
			if (s instanceof IQualifierType)
				s= SemanticUtil.getNestedType(((IQualifierType) s).getType(), TDEF | REF);

			if (t instanceof ICPPClassType && s instanceof ICPPClassType) {
				return calculateInheritanceDepth(CPPSemantics.MAX_INHERITANCE_DEPTH, s, t);
			}
		}
		if (t == s || (t != null && s != null && t.isSameType(s))) {
			return 0;
		}
		return -1;
	}

	/**
	 * [8.5.3] "cv1 T1" is reference-compatible with "cv2 T2" if T1 is reference-related
	 * to T2 and cv1 is the same cv-qualification as, or greater cv-qualification than, cv2.
	 * Note this is not a symmetric relation.
	 * @return The cost for converting or <code>null</code> if <code>cv1t1</code> is not
	 * reference-compatible with <code>cv2t2</code>
	 */
	private static final Cost isReferenceCompatible(IType cv1Target, IType cv2Source, boolean isImpliedObject) throws DOMException {
		int inheritanceDist= isReferenceRelated(cv1Target, cv2Source);
		if (inheritanceDist < 0)
			return null;
		final int cmp= compareQualifications(cv1Target, cv2Source);
		if (cmp < 0)
			return null;
		
		// 7.3.3.13 for overload resolution the implicit this pointer is treated as if 
		// it were a pointer to the derived class
		if (isImpliedObject) 
			inheritanceDist= 0;

		Cost cost= new Cost(cv2Source, cv1Target, Rank.IDENTITY);
		cost.setQualificationAdjustment(cmp);
		cost.setInheritanceDistance(inheritanceDist);
		return cost;
	}
	
	/**
	 * [4] Standard Conversions
	 * Computes the cost of using the standard conversion sequence from source to target.
	 * @param isImplicitThis handles the special case when members of different
	 *    classes are nominated via using-declarations. In such a situation the derived to
	 *    base conversion does not cause any costs.
	 * @throws DOMException
	 */
	private static final Cost checkStandardConversionSequence(IType source, boolean isLValue, IType target,
			boolean isImplicitThis) throws DOMException {
		final Cost cost= new Cost(source, target, Rank.IDENTITY);
		if (lvalue_to_rvalue(cost, isLValue))
			return cost;

		if (promotion(cost))
			return cost;
		
		if (conversion(cost, isImplicitThis)) 
			return cost;

		if (qualificationConversion(cost))
			return cost;

		// If we can't convert the qualifications, then we can't do anything
		cost.setRank(Rank.NO_MATCH);
		return cost;
	}

	/**
	 * [13.3.3.1.2] User-defined conversions
	 * @param source
	 * @param target
	 * @return
	 * @throws DOMException
	 */
	static final Cost checkUserDefinedConversionSequence(boolean sourceIsLValue, IType source, IType target, boolean deferUDC) throws DOMException {
		IType s= getNestedType(source, TDEF | CVTYPE | REF);
		IType t= getNestedType(target, TDEF | CVTYPE | REF);

		if (!(s instanceof ICPPClassType || t instanceof ICPPClassType)) {
			return null;
		}
		
		if (deferUDC) {
			Cost c= new Cost(source, target, Rank.USER_DEFINED_CONVERSION);
			c.setDeferredUDC(true);
			return c;
		}
		
		// 13.3.1.4 Copy initialization of class by user-defined conversion
		if (t instanceof ICPPClassType) {
			FunctionCost cost1= null;
			Cost cost2= null;
			ICPPConstructor[] ctors= ((ICPPClassType) t).getConstructors();
			CPPTemplates.instantiateFunctionTemplates(ctors, new IType[]{source}, sourceIsLValue ? LVBITSET : RVBITSET, null);

			for (ICPPConstructor ctor : ctors) {
				if (ctor != null && !(ctor instanceof IProblemBinding) && !ctor.isExplicit()) {
					final ICPPFunctionType ft = ctor.getType();
					final IType[] ptypes = ft.getParameterTypes();
					FunctionCost c1;
					if (ptypes.length == 0) {
						if (ctor.takesVarArgs()) {
							c1= new FunctionCost(ctor, new Cost(source, null, Rank.ELLIPSIS_CONVERSION));
						} else {
							continue;
						}
					} else {
						IType ptype= SemanticUtil.getNestedType(ptypes[0], TDEF);
						// We don't need to check the implicit conversion sequence if the type is void
						if (SemanticUtil.isVoidType(ptype)) 
							continue;
						if (ctor.getRequiredArgumentCount() > 1) 
							continue;
						
						c1= new FunctionCost(ctor, checkImplicitConversionSequence(ptype, source, sourceIsLValue, UDCMode.noUDC, false));
					}
					int cmp= c1.compareTo(null, cost1);
					if (cmp <= 0) {
						cost1= c1;
						cost2= new Cost(t, t, Rank.IDENTITY);
						cost2.setUserDefinedConversion(ctor);
						if (cmp == 0) {
							cost2.setAmbiguousUDC(true);
						}
					}
				}
			}
			if (s instanceof ICPPClassType) {
				ICPPMethod[] ops = SemanticUtil.getConversionOperators((ICPPClassType) s); 
				CPPTemplates.instantiateConversionTemplates(ops, target);
				for (final ICPPMethod op : ops) {
					if (op != null && !(op instanceof IProblemBinding)) {
						final IType returnType = op.getType().getReturnType();
						final IType uqReturnType= getNestedType(returnType, REF | TDEF | CVTYPE);
						final int dist = calculateInheritanceDepth(CPPSemantics.MAX_INHERITANCE_DEPTH, uqReturnType, t);
						if (dist >= 0) {
							final ICPPFunctionType ft = op.getType();
							IType implicitType= CPPSemantics.getImplicitType(op, ft.isConst(), ft.isVolatile());
							final Cost udcCost = isReferenceCompatible(getNestedType(implicitType, TDEF | REF), source, true);
							if (udcCost != null) {
								FunctionCost c1= new FunctionCost(op, udcCost);
								int cmp= c1.compareTo(null, cost1);
								if (cmp <= 0) {
									cost1= c1;
									cost2= new Cost(t, t, Rank.IDENTITY);
									if (dist > 0) {
										cost2.setInheritanceDistance(dist);
										cost2.setRank(Rank.CONVERSION);
									}
									cost2.setUserDefinedConversion(op);
									if (cmp == 0) {
										cost2.setAmbiguousUDC(true);
									}
								}
							}
						}
					}
				}
			}
			if (cost1 == null || cost1.getCost(0).getRank() == Rank.NO_MATCH)
				return null;
			
			return cost2;
		}
		
		// 13.3.1.5 Initialization by conversion function
		if (s instanceof ICPPClassType) {
			ICPPMethod[] ops = SemanticUtil.getConversionOperators((ICPPClassType) s); 
			CPPTemplates.instantiateConversionTemplates(ops, target);
			FunctionCost cost1= null;
			Cost cost2= null;
			for (final ICPPMethod op : ops) {
				if (op != null && !(op instanceof IProblemBinding)) {
					final IType returnType = op.getType().getReturnType();
					IType uqReturnType= getNestedType(returnType, TDEF | ALLCVQ);
					boolean isLValue = uqReturnType instanceof ICPPReferenceType
							&& !((ICPPReferenceType) uqReturnType).isRValueReference();
					Cost c2= checkImplicitConversionSequence(target, uqReturnType, isLValue, UDCMode.noUDC, false);
					if (c2.getRank() != Rank.NO_MATCH) {
						ICPPFunctionType ftype = op.getType();
						IType implicitType= CPPSemantics.getImplicitType(op, ftype.isConst(), ftype.isVolatile());
						final Cost udcCost = isReferenceCompatible(getNestedType(implicitType, TDEF | REF), source, true);
						if (udcCost != null) {
							FunctionCost c1= new FunctionCost(op, udcCost);
							int cmp= c1.compareTo(null, cost1);
							if (cmp <= 0) {
								cost1= c1;
								cost2= c2;
								cost2.setUserDefinedConversion(op);
								if (cmp == 0) {
									cost2.setAmbiguousUDC(true);
								}
							}
						}
					}
				}
			}
			if (cost1 == null || cost1.getCost(0).getRank() == Rank.NO_MATCH)
				return null;
			
			return cost2;
		}
		return null;
	}

	/**
	 * Calculates the number of edges in the inheritance path of <code>clazz</code> to
	 * <code>ancestorToFind</code>, returning -1 if no inheritance relationship is found.
	 * @param clazz the class to search upwards from
	 * @param ancestorToFind the class to find in the inheritance graph
	 * @return the number of edges in the inheritance graph, or -1 if the specified classes have
	 * no inheritance relation
	 * @throws DOMException
	 */
	static final int calculateInheritanceDepth(int maxdepth, IType type, IType ancestorToFind)
			throws DOMException {
		if (type == ancestorToFind || type.isSameType(ancestorToFind)) {
			return 0;
		}

		if (maxdepth > 0 && type instanceof ICPPClassType && ancestorToFind instanceof ICPPClassType) {
			ICPPClassType clazz = (ICPPClassType) type;
			if (clazz instanceof ICPPDeferredClassInstance) {
				clazz= (ICPPClassType) ((ICPPDeferredClassInstance) clazz).getSpecializedBinding();
			}
			
			for (ICPPBase cppBase : clazz.getBases()) {
				IBinding base= cppBase.getBaseClass();
				if (base instanceof IType) {
					IType tbase= (IType) base;
					if (tbase.isSameType(ancestorToFind) || 
							(ancestorToFind instanceof ICPPSpecialization &&  // allow some flexibility with templates 
							((IType)((ICPPSpecialization) ancestorToFind).getSpecializedBinding()).isSameType(tbase))) {
						return 1;
					}

					if (tbase instanceof ICPPClassType) {
						int n= calculateInheritanceDepth(maxdepth - 1, tbase, ancestorToFind);
						if (n > 0)
							return n + 1;
					}
				}
			}
		}

		return -1;
	}

	/**
	 * Attempts the conversions below and returns whether this completely converts the source to
	 * the target type.
	 * [4.1] Lvalue-to-rvalue conversion
	 * [4.2] array-to-ptr
	 * [4.3] function-to-ptr
	 */
	private static final boolean lvalue_to_rvalue(final Cost cost, boolean isLValue) throws DOMException {
		// target should not be a reference here.
		boolean isConverted= false;
		IType target = getNestedType(cost.target, REF | TDEF);
		IType source= getNestedType(cost.source, REF | TDEF);
		
		// 4.1 lvalue to rvalue
		if (isLValue) {
			// 4.1 lvalue of non-function and non-array
			if (!(source instanceof IFunctionType) && !(source instanceof IArrayType)) {
				// 4.1 if T is a non-class type, the type of the rvalue is the cv-unqualified version of T
				IType unqualifiedSrcRValue= getNestedType(source, ALLCVQ | TDEF | REF);
				if (unqualifiedSrcRValue instanceof ICPPClassType) {
					cost.setRank(Rank.NO_MATCH);
					return true;
				} else {
					source= unqualifiedSrcRValue;
				}
				isConverted= true;
			}
		}
		
		// 4.2 array to pointer conversion
		if (!isConverted && source instanceof IArrayType) {
			final IArrayType arrayType= (IArrayType) source;
			
			if (target instanceof IPointerType) {
				final IType targetPtrTgt= getNestedType(((IPointerType) target).getType(), TDEF);
				
				// 4.2-2 a string literal can be converted to pointer to char
				if (!(targetPtrTgt instanceof IQualifierType) || !((IQualifierType) targetPtrTgt).isConst()) {
					IType tmp= arrayType.getType();
					if (tmp instanceof IQualifierType && ((IQualifierType) tmp).isConst()) {
						tmp= ((IQualifierType) tmp).getType();
						if (tmp instanceof CPPBasicType) {
							IASTExpression val = ((CPPBasicType) tmp).getCreatedFromExpression();
							if (val instanceof IASTLiteralExpression) {
								IASTLiteralExpression lit= (IASTLiteralExpression) val;
								if (lit.getKind() == IASTLiteralExpression.lk_string_literal) {
									source= new CPPPointerType(tmp, false, false);
									cost.setQualificationAdjustment(getCVQualifier(targetPtrTgt).isVolatile() ? 2 : 1);
									isConverted= true;
								}
							}
						}
					}
				}
			}
			if (!isConverted && (target instanceof IPointerType || target instanceof IBasicType)) {
				source = new CPPPointerType(getNestedType(arrayType.getType(), TDEF));
				isConverted= true;
			}
		}

		// 4.3 function to pointer conversion
		if (!isConverted && target instanceof IPointerType) {
			final IType targetPtrTgt= getNestedType(((IPointerType) target).getType(), TDEF);
			if (targetPtrTgt instanceof IFunctionType && source instanceof IFunctionType) {
				source = new CPPPointerType(source);
				isConverted= true;
			} 
		}

		if (source == null || target == null) {
			cost.setRank(Rank.NO_MATCH);
			return true;
		} 
		cost.source= source;
		cost.target= target;
		return source.isSameType(target);
	}
	
	/**
	 * [4.4] Qualifications 
	 * @param cost
	 * @throws DOMException
	 */
	private static final boolean qualificationConversion(Cost cost) throws DOMException{
		IType s = cost.source;
		IType t = cost.target;
		boolean constInEveryCV2k = true;
		boolean firstPointer= true;
		int adjustments= 0;
		while (true) {
			s= getNestedType(s, TDEF | REF);
			t= getNestedType(t, TDEF | REF);
			if (s instanceof IPointerType && t instanceof IPointerType) {
				adjustments <<= 2;
				final int cmp= compareQualifications(t, s);  // is t more qualified than s?
				if (cmp < 0 || (cmp > 0 && !constInEveryCV2k)) {
					return false;
				} else {
					final boolean sIsPtrToMember = s instanceof ICPPPointerToMemberType;
					final boolean tIsPtrToMember = t instanceof ICPPPointerToMemberType;
					if (sIsPtrToMember != tIsPtrToMember) {
						return false;
					} else if (sIsPtrToMember) {
						final IType sMemberOf = ((ICPPPointerToMemberType) s).getMemberOfClass();
						final IType tMemberOf = ((ICPPPointerToMemberType) t).getMemberOfClass();
						if (sMemberOf == null || tMemberOf == null || !sMemberOf.isSameType(tMemberOf)) {
							return false;
						}
					}
				}

				final IPointerType tPtr = (IPointerType) t;
				final IPointerType sPtr = (IPointerType) s;
				constInEveryCV2k &= (firstPointer || tPtr.isConst());
				s= sPtr.getType();
				t= tPtr.getType();
				firstPointer= false;
				adjustments |= cmp;
			} else {
				break;
			}
		}

		adjustments <<= 2;
		int cmp= compareQualifications(t, s);  // is t more qualified than s?
		if (cmp < 0 || (cmp > 0 && !constInEveryCV2k)) {
			return false;
		} 

		adjustments |= cmp;
		s= getNestedType(s, ALLCVQ | TDEF | REF);
		t= getNestedType(t, ALLCVQ | TDEF | REF);
		
		if (adjustments > 0) {
			cost.setQualificationAdjustment(adjustments);
		}
		return s != null && t != null && s.isSameType(t);
	}

	/**
	 * Attempts promotions and returns whether the promotion converted the type.
	 * 
	 * [4.5] [4.6] Promotion
	 * 
	 * 4.5-1 char, signed char, unsigned char, short int or unsigned short int
	 * can be converted to int if int can represent all the values of the source
	 * type, otherwise they can be converted to unsigned int.
	 * 4.5-2 wchar_t or an enumeration can be converted to the first of the
	 * following that can hold it: int, unsigned int, long unsigned long.
	 * 4.5-4 bool can be promoted to int 
	 * 4.6 float can be promoted to double
	 * @throws DOMException
	 */
	private static final boolean promotion(Cost cost) throws DOMException{
		IType src = cost.source;
		IType trg = cost.target;

		boolean canPromote= false;
		if (trg instanceof IBasicType) {
			IBasicType basicTgt = (IBasicType) trg;
			final Kind tKind = basicTgt.getKind();

			if (src instanceof IBasicType) {
				final IBasicType basicSrc = (IBasicType) src;
				Kind sKind = basicSrc.getKind();
				if (tKind == Kind.eInt) {
					switch (sKind) {
					case eInt: // short, and unsigned short
						if (basicSrc.isShort()) {
							canPromote= true;
						}
						break;
					case eChar:
					case eBoolean:
					case eWChar:
					case eUnspecified: // treat unspecified as int
						canPromote= true;
						break;
					default:
						break;
					}
				} else if (tKind == Kind.eDouble && sKind == Kind.eFloat) {
					canPromote= true;
				}
			} else if (src instanceof IEnumeration) {
				if (tKind == Kind.eInt || tKind == Kind.eUnspecified) {
					if (trg instanceof ICPPBasicType) {
						int qualifiers = ArithmeticConversion.getEnumIntTypeModifiers((IEnumeration) src);
						int targetModifiers = ((ICPPBasicType) trg).getModifiers();
						if (qualifiers == (targetModifiers & (IBasicType.IS_LONG | IBasicType.IS_LONG_LONG | IBasicType.IS_SHORT | IBasicType.IS_UNSIGNED))) {
							canPromote = true;
						}
					} else {
						canPromote = true;
					}
				}
			}
		}
		if (canPromote) {
			cost.setRank(Rank.PROMOTION);
			return true;
		}
		return false;
	}

	/**
	 * Attempts conversions and returns whether the conversion succeeded.
	 * [4.7]  Integral conversions
	 * [4.8]  Floating point conversions
	 * [4.9]  Floating-integral conversions
	 * [4.10] Pointer conversions
	 * [4.11] Pointer to member conversions
	 */
	private static final boolean conversion(Cost cost, boolean forImplicitThis) throws DOMException{
		final IType s = cost.source;
		final IType t = cost.target;

		if (t instanceof IBasicType) {
			// 4.7 integral conversion
			// 4.8 floating point conversion
			// 4.9 floating-integral conversion
			if (s instanceof IBasicType || s instanceof IEnumeration) {
				// 4.7 An rvalue of an integer type can be converted to an rvalue of another integer type.  
				// An rvalue of an enumeration type can be converted to an rvalue of an integer type.
				cost.setRank(Rank.CONVERSION);
				return true;
			} 
			// 4.12 pointer or pointer to member type can be converted to an rvalue of type bool
			final Kind tgtKind = ((IBasicType) t).getKind();
			if (tgtKind == Kind.eBoolean && s instanceof IPointerType) {
				cost.setRank(Rank.CONVERSION_PTR_BOOL);
				return true;
			} 
		}
		
		if (t instanceof IPointerType) {
			IPointerType tgtPtr= (IPointerType) t;
			if (s instanceof CPPBasicType) {
				// 4.10-1 an integral constant expression of integer type that evaluates to 0 can
				// be converted to a pointer type
				// 4.11-1 same for pointer to member
				IASTExpression exp = ((CPPBasicType) s).getCreatedFromExpression();
				if (exp != null) {
					Long val= Value.create(exp, Value.MAX_RECURSION_DEPTH).numericalValue();
					if (val != null && val == 0) {
						cost.setRank(Rank.CONVERSION);
						return true;
					}
				}
				return false;
			}
			if (s instanceof IPointerType) {
				IPointerType srcPtr= (IPointerType) s;
				// 4.10-2 an rvalue of type "pointer to cv T", where T is an object type can be
				// converted to an rvalue of type "pointer to cv void"
				IType tgtPtrTgt= getNestedType(tgtPtr.getType(), TDEF | CVTYPE | REF);
				if (SemanticUtil.isVoidType(tgtPtrTgt)) {
					cost.setRank(Rank.CONVERSION);
					cost.setInheritanceDistance(Short.MAX_VALUE); // mstodo add distance to last base class
					CVQualifier cv= getCVQualifier(srcPtr.getType());
					cost.source= new CPPPointerType(addQualifiers(CPPSemantics.VOID_TYPE, cv.isConst(), cv.isVolatile()));
					return false; 
				}
				
				final boolean tIsPtrToMember = t instanceof ICPPPointerToMemberType;
				final boolean sIsPtrToMember = s instanceof ICPPPointerToMemberType;
				if (!tIsPtrToMember && !sIsPtrToMember) {
					// 4.10-3 An rvalue of type "pointer to cv D", where D is a class type can be converted
					// to an rvalue of type "pointer to cv B", where B is a base class of D.
					IType srcPtrTgt= getNestedType(srcPtr.getType(), TDEF | CVTYPE | REF);
					if (tgtPtrTgt instanceof ICPPClassType && srcPtrTgt instanceof ICPPClassType) {
						int depth= calculateInheritanceDepth(CPPSemantics.MAX_INHERITANCE_DEPTH, srcPtrTgt, tgtPtrTgt);
						if (depth == -1) {
							cost.setRank(Rank.NO_MATCH);
							return true;
						}
						if (depth > 0) {
							if (!forImplicitThis) {
								cost.setRank(Rank.CONVERSION);
								cost.setInheritanceDistance(depth);
							}
							CVQualifier cv= getCVQualifier(srcPtr.getType());
							cost.source= new CPPPointerType(addQualifiers(tgtPtrTgt, cv.isConst(), cv.isVolatile()));
						}
						return false;
					}
				} else if (tIsPtrToMember && sIsPtrToMember) {
					// 4.11-2 An rvalue of type "pointer to member of B of type cv T", where B is a class type, 
					// can be converted to an rvalue of type "pointer to member of D of type cv T" where D is a
					// derived class of B
					ICPPPointerToMemberType spm = (ICPPPointerToMemberType) s;
					ICPPPointerToMemberType tpm = (ICPPPointerToMemberType) t;
					IType st = spm.getType();
					IType tt = tpm.getType();
					if (st != null && tt != null && st.isSameType(tt)) {
						int depth= calculateInheritanceDepth(CPPSemantics.MAX_INHERITANCE_DEPTH,
								tpm.getMemberOfClass(), spm.getMemberOfClass());
						if (depth == -1) {
							cost.setRank(Rank.NO_MATCH);
							return true;
						}
						if (depth > 0) {
							cost.setRank(Rank.CONVERSION);
							cost.setInheritanceDistance(depth);
							cost.source = new CPPPointerToMemberType(spm.getType(),
									tpm.getMemberOfClass(), spm.isConst(), spm.isVolatile());
						}
						return false;
					}
				}
			}
		}
		return false;
	}
}
