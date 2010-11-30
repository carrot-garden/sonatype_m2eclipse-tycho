/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sonatype.tycho.m2e.internal;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.m2e.core.core.IMavenConstants;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.jdt.BuildPathManager;
import org.eclipse.m2e.tests.common.AbstractLifecycleMappingTest;
import org.eclipse.pde.internal.core.natures.PDE;

@SuppressWarnings( "restriction" )
public class MavenBundlePluginTest
    extends AbstractLifecycleMappingTest
{

    public void testImport()
        throws Exception
    {
        IMavenProjectFacade facade = importMavenProject( "projects/maven-bundle-plugin/bundle", "pom.xml" );
        assertNotNull( "Expected not null maven project facade", facade );

        // make sure natures are setup right
        IProject project = facade.getProject();
        assertNotNull( "Expected not null project", project );
        assertTrue( project.hasNature( PDE.PLUGIN_NATURE ) );
        assertTrue( project.hasNature( JavaCore.NATURE_ID ) );
        assertTrue( project.hasNature( IMavenConstants.NATURE_ID ) );

        // make sure classpath is setup right
        IJavaProject javaProject = JavaCore.create( project );
        IClasspathEntry[] cp = javaProject.getRawClasspath();
        assertEquals( 3, cp.length );
        assertEquals( new Path( BuildPathManager.CONTAINER_ID ), cp[2].getPath() );

        // make sure manifest is generated properly
        project.build( IncrementalProjectBuilder.FULL_BUILD, monitor );
        waitForJobsToComplete();
        assertTrue( project.getFile( "META-INF/MANIFEST.MF" ).isAccessible() );

        // make sure PDE builder is not enabled
        ICommand[] builders = project.getDescription().getBuildSpec();
        assertEquals( 2, builders.length );
        assertEquals( "org.eclipse.jdt.core.javabuilder", builders[0].getBuilderName() );
        assertEquals( "org.eclipse.m2e.core.maven2Builder", builders[1].getBuilderName() );
    }

    public void testImportDespiteErrorsInExecutionPlan()
        throws Exception
    {
        IMavenProjectFacade facade = importMavenProject( "projects/maven-bundle-plugin/unresolvable-plugin", "pom.xml" );

        // make sure natures are setup right
        IProject project = facade.getProject();
        assertTrue( project.hasNature( PDE.PLUGIN_NATURE ) );
        assertTrue( project.hasNature( IMavenConstants.NATURE_ID ) );

        // make sure PDE builder is not enabled
        ICommand[] builders = project.getDescription().getBuildSpec();
        assertEquals( 1, builders.length );
        assertEquals( "org.eclipse.m2e.core.maven2Builder", builders[0].getBuilderName() );
    }
}
