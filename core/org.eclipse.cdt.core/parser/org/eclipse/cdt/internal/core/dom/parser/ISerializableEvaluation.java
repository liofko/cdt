/*******************************************************************************
 * Copyright (c) 2009, 2012 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Markus Schorn - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.core.dom.parser;

import org.eclipse.core.runtime.CoreException;

/**
 * Interface for marshalling ICPPEvaluation objects for storage in the index.
 */
public interface ISerializableEvaluation {
	/**
	 * Marshals an ICPPEvaluation object for storage in the index.
	 *
	 * @param  buffer The buffer that will hold the marshalled ICPPEvaluation object.
	 * @param  includeValue Specifies whether nested IValue objects should be marshalled as well.
	 * */
	void marshal(ITypeMarshalBuffer buffer, boolean includeValue) throws CoreException;
}
