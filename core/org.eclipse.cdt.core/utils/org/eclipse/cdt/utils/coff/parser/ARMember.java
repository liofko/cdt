/**********************************************************************
 * Copyright (c) 2002,2003 QNX Software Systems and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * QNX Software Systems - Initial API and implementation
***********************************************************************/
package org.eclipse.cdt.utils.coff.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.eclipse.cdt.core.IBinaryParser.ISymbol;
import org.eclipse.cdt.utils.Addr2line;
import org.eclipse.cdt.utils.CPPFilt;
import org.eclipse.cdt.utils.CygPath;
import org.eclipse.cdt.utils.ICygwinToolsProvider;
import org.eclipse.cdt.utils.coff.Coff;
import org.eclipse.cdt.utils.coff.PE;
import org.eclipse.cdt.utils.coff.PEArchive;
import org.eclipse.core.runtime.IPath;

/**
 */
public class ARMember extends BinaryObject {
	PEArchive.ARHeader header;

	public ARMember(IPath p, PEArchive.ARHeader h, ICygwinToolsProvider provider) throws IOException {
		super(p, h.getPE(), provider);
		header = h;
	}

	/**
	 * @see org.eclipse.cdt.core.model.IBinaryParser.IBinaryFile#getContents()
	 */
	public InputStream getContents() {
		InputStream stream = null;
		if (path != null && header != null) {
			try {
				stream = new ByteArrayInputStream(header.getObjectData());
			} catch (IOException e) {
			}
		}
		if (stream == null) {
			stream = super.getContents();
		}
		return stream;
	}

	/**
	 * @see org.eclipse.cdt.core.model.IBinaryParser.IBinaryObject#getName()
	 */
	public String getName() {
		if (header != null) {
			return header.getObjectName();
		}
		return "";
	}

	protected PE getPE() throws IOException {
		if (header != null) {
			return header.getPE();
		}
		throw new IOException("No file assiocated with Binary");
	}

	protected void addSymbols(Coff.Symbol[] peSyms, byte[] table, Addr2line addr2line, CPPFilt cppfilt, CygPath cypath, List list) {
		for (int i = 0; i < peSyms.length; i++) {
			if (peSyms[i].isFunction() || peSyms[i].isPointer() ||peSyms[i].isArray()) {
				String name = peSyms[i].getName(table);
				if (name == null || name.trim().length() == 0 ||
					!Character.isJavaIdentifierStart(name.charAt(0))) {
					continue;
				}
				Symbol sym = new Symbol(this);
				sym.type = peSyms[i].isFunction() ? ISymbol.FUNCTION : ISymbol.VARIABLE;

				sym.name = name;
				sym.addr = peSyms[i].n_value;
				list.add(sym);
			}
		}
	}

}
