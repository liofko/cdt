/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.cdt.internal.core.search;

import java.util.ArrayList;
import java.util.HashSet;

import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.search.ICSearchScope;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

public class CSearchScope implements ICSearchScope {

	private ArrayList elements;

	/* The paths of the resources in this search scope*/
	private IPath[] paths;
	private boolean[] pathWithSubFolders;
	private int pathsCount;
	
	private IPath[] enclosingProjects;
	
	public CSearchScope() {
		super();
		this.initialize();
	}
	
	protected void initialize() {
		this.paths = new IPath[1];
		this.pathWithSubFolders = new boolean[1];
		this.pathsCount = 0;
		this.enclosingProjects = new IPath[0];
	}

	private void addEnclosingProject(IPath path) {
	  int length = this.enclosingProjects.length;
	  for (int i = 0; i < length; i++) {
		  if (this.enclosingProjects[i].equals(path)) return;
	  }
	  System.arraycopy(
		  this.enclosingProjects,
		  0,
		  this.enclosingProjects = new IPath[length+1],
		  0,
		  length);
	  this.enclosingProjects[length] = path;
    }

	public void add(ICProject cProject, boolean includesPrereqProjects, HashSet visitedProjects) {
		IProject project = cProject.getProject();
		if (!project.isAccessible() || !visitedProjects.add(project)) return;
	
		this.addEnclosingProject(project.getFullPath());
   }
   /**
    * Adds the given path to this search scope. Remember if subfolders need to be included as well.
    */
   private void add(IPath path, boolean withSubFolders) {
	  if (this.paths.length == this.pathsCount) {
		  System.arraycopy(
			  this.paths,
			  0,
			  this.paths = new IPath[this.pathsCount * 2],
			  0,
			  this.pathsCount);
		  System.arraycopy(
			  this.pathWithSubFolders,
			  0,
			  this.pathWithSubFolders = new boolean[this.pathsCount * 2],
			  0,
			  this.pathsCount);
	  }
	  this.paths[this.pathsCount] = path;
	  this.pathWithSubFolders[this.pathsCount++] = withSubFolders; 
   }

   public boolean encloses(String resourcePathString) {
	  IPath resourcePath;
	  int separatorIndex = -1; //resourcePathString.indexOf(JAR_FILE_ENTRY_SEPARATOR);
	  if (separatorIndex != -1) {
		  resourcePath = 
			  new Path(resourcePathString.substring(0, separatorIndex)).
				  append(new Path(resourcePathString.substring(separatorIndex+1)));
	  } else {
			  resourcePath = new Path(resourcePathString);
	  }
	  return this.encloses(resourcePath);
   }

   /**
    * Returns whether this search scope encloses the given path.
    */
   private boolean encloses(IPath path) {
	  for (int i = 0; i < this.pathsCount; i++) {
		  if (this.pathWithSubFolders[i]) {
			  if (this.paths[i].isPrefixOf(path)) {
				  return true;
			  }
		  } else {
			  // if not looking at subfolders, this scope encloses the given path 
			  // if this path is a direct child of the scope's ressource
			  // or if this path is the scope's resource (see bug 13919 Declaration for package not found if scope is not project)
			  IPath scopePath = this.paths[i];
			  if (scopePath.isPrefixOf(path) 
				  && ((scopePath.segmentCount() == path.segmentCount() - 1)
					  || (scopePath.segmentCount() == path.segmentCount()))) {
				  return true;
			  }
		  }
	  }
	  return false;
   }

   public boolean encloses(ICElement element) {
	   if (this.elements != null) {
	 	  for (int i = 0, length = this.elements.size(); i < length; i++) {
	  		  ICElement scopeElement = (ICElement)this.elements.get(i);
	 		  ICElement searchedElement = element;
			  while (searchedElement != null) {
				  if (searchedElement.equals(scopeElement)) {
					  return true;
				  } else {
					  searchedElement = searchedElement.getParent();
				  }
			  }
		  }
		  return false;
	   } else {
		   return this.encloses(this.fullPath(element));
	   }
   }

   public IPath[] enclosingProjects() {
 	  return this.enclosingProjects;
   }
  
   private IPath fullPath(ICElement element) {
 	  return null;
   }
}
