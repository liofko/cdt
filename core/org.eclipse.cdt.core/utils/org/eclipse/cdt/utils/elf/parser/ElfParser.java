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
package org.eclipse.cdt.utils.elf.parser;
 
import java.io.IOException;

import org.eclipse.cdt.core.AbstractCExtension;
import org.eclipse.cdt.core.IBinaryParser;
import org.eclipse.cdt.utils.elf.AR;
import org.eclipse.cdt.utils.elf.Elf;
import org.eclipse.cdt.utils.elf.Elf.Attribute;
import org.eclipse.core.runtime.IPath;

/**
 */
public class ElfParser extends AbstractCExtension implements IBinaryParser {
	byte [] fCachedByteArray;
	IPath   fCachedPathEntry;
	boolean fCachedIsAR;
	
	public IBinaryFile getBinary(IPath path) throws IOException {
		if (path == null) {
			throw new IOException("path is null");
		}

		BinaryFile binary = null;
		try {
			Elf.Attribute attribute = null;
			 
			//Try our luck with the cached entry first, then clear it
			if(fCachedPathEntry != null && fCachedPathEntry.equals(path)) {			
				try {
					//Don't bother with ELF stuff if this is an archive
					if(fCachedIsAR) {
						return new BinaryArchive(path);
					} 
					//Well, if it wasn't an archive, go for broke
					attribute = Elf.getAttributes(fCachedByteArray);
				} catch(Exception ex) {
					attribute = null;
				} finally {
					fCachedPathEntry = null;
					fCachedByteArray = null;
				}
 			}

			//Take a second run at it if the cache failed. 			
 			if(attribute == null) {
				attribute = Elf.getAttributes(path.toOSString());
 			}

			if (attribute != null) {
				switch (attribute.getType()) {
					case Attribute.ELF_TYPE_EXE :
						binary = new BinaryExecutable(path);
						break;

					case Attribute.ELF_TYPE_SHLIB :
						binary = new BinaryShared(path);
						break;

					case Attribute.ELF_TYPE_OBJ :
						binary = new BinaryObject(path);
						break;

					case Attribute.ELF_TYPE_CORE :
						BinaryObject obj = new BinaryObject(path);
						obj.setType(IBinaryFile.CORE);
						binary = obj;
						break;
				}
			}
		} catch (IOException e) {
			binary = new BinaryArchive(path);
		}
		return binary;
	}

	/**
	 * @see org.eclipse.cdt.core.model.IBinaryParser#getFormat()
	 */
	public String getFormat() {
		return "ELF";
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.IBinaryParser#isBinary(byte[], org.eclipse.core.runtime.IPath)
	 */
	public boolean isBinary(byte[] array, IPath path) {
		boolean isBinaryReturnValue = false;

		if(Elf.isElfHeader(array)) {
			isBinaryReturnValue = true;
			fCachedIsAR = false;			
		} else if(AR.isARHeader(array)) {
			isBinaryReturnValue = true;
			fCachedIsAR = true;
		}
		
		//If it is a binary, then cache the array in anticipation that we will be asked to do something with it
		if(isBinaryReturnValue && array.length > 0) {
			fCachedPathEntry = path;
			fCachedByteArray = new byte[array.length];
			System.arraycopy(array, 0, fCachedByteArray, 0, array.length);
		}
		
		return isBinaryReturnValue;
	}

}
