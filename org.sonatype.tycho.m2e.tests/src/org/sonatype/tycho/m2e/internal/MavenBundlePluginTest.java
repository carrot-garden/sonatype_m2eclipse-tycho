/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sonatype.tycho.m2e.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Manifest;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.internal.preferences.MavenConfigurationImpl;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.ResolverConfiguration;
import org.eclipse.m2e.jdt.IClasspathManager;
import org.eclipse.m2e.tests.common.AbstractLifecycleMappingTest;
import org.eclipse.m2e.tests.common.WorkspaceHelpers;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.internal.core.natures.PDE;

@SuppressWarnings( "restriction" )
public class MavenBundlePluginTest
    extends AbstractLifecycleMappingTest
{
    public void testImport()
        throws Exception
    {
        IMavenProjectFacade facade = importMavenProject( "projects/maven-bundle-plugin/bundle", "pom.xml" );
        assertPDEPluginProject( facade, "META-INF/MANIFEST.MF" );
    }

    private void assertPDEPluginProject( IMavenProjectFacade facade, String manifestRelPath )
        throws CoreException, JavaModelException, InterruptedException
    {
        assertNotNull( "Expected not null maven project facade", facade );

        assertPDEPluginProject( facade.getProject(), manifestRelPath );
    }

    protected void assertPDEPluginProject( IProject project, String manifestRelPath )
        throws CoreException, JavaModelException, InterruptedException
    {
        assertNotNull( "Expected not null project", project );
        WorkspaceHelpers.assertNoErrors( project );

        // make sure natures are setup right
        assertTrue( project.hasNature( PDE.PLUGIN_NATURE ) );
        assertTrue( project.hasNature( JavaCore.NATURE_ID ) );
        assertTrue( project.hasNature( IMavenConstants.NATURE_ID ) );

        // make sure classpath is setup right
        IJavaProject javaProject = JavaCore.create( project );
        IClasspathEntry[] cp = javaProject.getRawClasspath();
        assertEquals( 3, cp.length );
        assertEquals( new Path( IClasspathManager.CONTAINER_ID ), cp[2].getPath() );

        // make sure manifest is generated properly
        project.build( IncrementalProjectBuilder.FULL_BUILD, monitor );
        waitForJobsToComplete();
        assertTrue( project.getFile( manifestRelPath ).isAccessible() );

        // make sure PDE builder is not enabled
        ICommand[] builders = project.getDescription().getBuildSpec();
        assertEquals( 2, builders.length );
        assertEquals( "org.eclipse.jdt.core.javabuilder", builders[0].getBuilderName() );
        assertEquals( "org.eclipse.m2e.core.maven2Builder", builders[1].getBuilderName() );
    }

    // XXX disabled due to https://issues.sonatype.org/browse/MNGECLIPSE-2724
    public void _testImportDespiteErrorsInExecutionPlan()
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

    public void testImportProjectWithBundlePackaging()
        throws Exception
    {
        IMavenProjectFacade facade = importMavenProject( "projects/maven-bundle-plugin/bundle-packaging", "pom.xml" );
        assertPDEPluginProject( facade, "META-INF/MANIFEST.MF" );

        // make sure full bundle is not packaged during workspace build
        IFile bundle = facade.getProject().getFile( "target/bundle-packaging-0.0.1-SNAPSHOT.jar" );
        bundle.refreshLocal( IResource.DEPTH_ZERO, monitor );
        assertFalse( bundle.exists() );
    }

    public void testDefaultManifestLocation()
        throws Exception
    {
        IMavenProjectFacade facade = importMavenProject( "projects/maven-bundle-plugin/manifestlocation", "pom.xml" );
        assertPDEPluginProject( facade, "target/classes/META-INF/MANIFEST.MF" );

        // make sure no META-INF/MANIFEST.MF
        IFile manifest = facade.getProject().getFile( "META-INF/MANIFEST.MF" );
        manifest.getParent().refreshLocal( IResource.DEPTH_INFINITE, monitor );
        assertFalse( manifest.exists() );
    }

    public void testEmbedDependency()
        throws Exception
    {
        IMavenProjectFacade maven =
            importMavenProject( "projects/maven-bundle-plugin/embed-dependency/maven", "pom.xml" );

        IPluginModelBase model = PluginRegistry.findModel( maven.getProject() );
        assertNotNull( model );

        IJavaProject javaProject = JavaCore.create( maven.getProject() );
        IClasspathEntry[] cp = javaProject.getRawClasspath();
        assertEquals( 2, cp.length );
        assertEquals( new Path( IClasspathManager.CONTAINER_ID ), cp[1].getPath() );
        assertTrue( cp[1].isExported() );

        IProject project = createExisting( "pde", "projects/maven-bundle-plugin/embed-dependency/pde" );

        workspace.build( IncrementalProjectBuilder.FULL_BUILD, monitor );
        workspace.build( IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor );
        waitForJobsToComplete();

        assertNoErrors( maven.getProject() );
        assertNoErrors( project );
    }

    public void testManifestGenerationAfterPomChange()
        throws Exception
    {
        IMavenProjectFacade facade = importMavenProject( "projects/maven-bundle-plugin/pom-change", "pom.xml" );

        IProject project = facade.getProject();
        IFile mfile = project.getFile( "META-INF/MANIFEST.MF" );

        assertPDEPluginProject( facade, "META-INF/MANIFEST.MF" );
        assertNoErrors( project );
        Manifest mf = loadManifest( mfile );
        assertEquals( "maven-bundle-plugin.pom-change", mf.getMainAttributes().getValue( "Bundle-SymbolicName" ) );

        copyContent( project, new File( "projects/maven-bundle-plugin/pom-change/pom.xml-changed-symbolic-name" ),
                     "pom.xml" );
        workspace.build( IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor );
        waitForJobsToComplete();
        assertNoErrors( project );

        mf = loadManifest( mfile );
        assertEquals( "pom-change;singleton:=true", mf.getMainAttributes().getValue( "Bundle-SymbolicName" ) );
    }

    public void testRequestConfigurationUpdateAfterManifestLocationChange()
        throws Exception
    {
        IMavenProjectFacade facade = importMavenProject( "projects/maven-bundle-plugin/pom-change", "pom.xml" );
        IProject project = facade.getProject();

        assertPDEPluginProject( facade, "META-INF/MANIFEST.MF" );
        assertNoErrors( project );

        copyContent( project, new File( "projects/maven-bundle-plugin/pom-change/pom.xml-changed-manifest-location" ),
                     "pom.xml" );
        WorkspaceHelpers.assertErrorMarker( IMavenConstants.MARKER_CONFIGURATION_ID,
                                            "Project configuration is not up-to-date with pom.xml. Run project configuration update",
                                            null, null, project );
    }

    public void testEmbedWorkspaceDependency()
        throws Exception
    {
//        ( (MavenConfigurationImpl) mavenConfiguration ).setDebugOutput( true );

        IProject[] projects = importProjects( "projects/maven-bundle-plugin/embed-workspace-dependency", //
                                              new String[] { "bundle/pom.xml", "dependency/pom.xml" }, //
                                              new ResolverConfiguration() );
        waitForJobsToComplete();

        assertPDEPluginProject( projects[0], "META-INF/MANIFEST.MF" );

        // compile classes and generate manifest
        workspace.build( IncrementalProjectBuilder.FULL_BUILD, monitor );
        waitForJobsToComplete();

        // make sure workspace dependency was scanned for exported packages
        IFile mfile = projects[0].getFile( "META-INF/MANIFEST.MF" );
        Manifest mf = loadManifest( mfile );
        assertEquals( "dependency", mf.getMainAttributes().getValue( "Export-Package" ) );
    }

    private Manifest loadManifest( IFile mfile )
        throws IOException, CoreException
    {
        InputStream is = mfile.getContents();
        try
        {
            return new Manifest( is );
        }
        finally
        {
            is.close();
        }
    }
}
