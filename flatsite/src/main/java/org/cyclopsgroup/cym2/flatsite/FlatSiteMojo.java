package org.cyclopsgroup.cym2.flatsite;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.Date;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;

/**
 * @author <a href="mailto:jiaqi.guo@gmail.com">Jiaqi Guo</a>
 * @description Generate site from flatsite templates
 * @goal flatsite
 */
public class FlatSiteMojo
    extends AbstractMojo
{
    /**
     * @parameter expression="${basedir}/target/site
     * @required
     */
    private File flatsiteOutputDirectory;

    /**
     * @parameter expression="${basedir}/src/flatsite
     * @required
     */
    private File flatsiteSourceDirectory;

    /**
     * @parameter expression="default_layout.vm"
     * @required
     */
    private String layout;

    /**
     * @parameter expression="${project}"
     * @readonly
     * @required
     */
    private MavenProject project;

    /**
     * @parameter expression=".vm"
     * @required
     */
    private String templateSuffix;

    private VelocityEngine velocityEngine;

    private XmlTool xmltool = new XmlTool();

    private void copyDirectory( File fromDirectory, File toDirectory )
        throws IOException
    {
        if ( !fromDirectory.isDirectory() )
        {
            return;
        }
        if ( !toDirectory.isDirectory() )
        {
            getLog().info( "Making directory " + toDirectory );
            toDirectory.mkdirs();
        }
        for ( File file : fromDirectory.listFiles() )
        {
            if ( file.getName().charAt( 0 ) == '.' )
            {
                continue;
            }
            else if ( file.isDirectory() )
            {
                copyDirectory( file, new File( toDirectory, file.getName() ) );
            }
            else
            {
                getLog().info( "Copy file from " + file + " into " + toDirectory );
                FileUtils.copyFileToDirectory( file, toDirectory );
            }
        }
    }

    /**
     * @inheritDoc
     */
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( !flatsiteSourceDirectory.isDirectory() )
        {
            getLog().info( flatsiteSourceDirectory + " doesn't exist. There's nothing to do" );
            return;
        }
        if ( !flatsiteOutputDirectory.isDirectory() )
        {
            getLog().info( "Makding destination directory " + flatsiteOutputDirectory );
            flatsiteOutputDirectory.mkdirs();
        }

        try
        {
            ExtendedProperties props = new ExtendedProperties();
            props.load( getClass().getClassLoader().getResourceAsStream( "flatsite-velocity.properties" ) );
            getLog().info( props.getString( "file.resource.loader.path" ) );
            velocityEngine = new VelocityEngine();
            velocityEngine.setExtendedProperties( props );
            velocityEngine.setProperty( "file.resource.loader.path", flatsiteSourceDirectory.getAbsolutePath() );
            velocityEngine.init();
            generateSiteDirectory( "" );
            copyDirectory( new File( flatsiteSourceDirectory, "resources" ), flatsiteOutputDirectory );
        }
        catch ( Exception e )
        {
            getLog().error( "Velocity error", e );
        }
    }

    private void generateSiteDirectory( String relativeDirectory )
        throws IOException
    {
        File currentSourceDirectory = new File( flatsiteSourceDirectory, "content/" + relativeDirectory );
        File[] files = currentSourceDirectory.listFiles();
        for ( File file : files )
        {
            if ( file.getName().charAt( 0 ) == '.' )
            {
                continue;
            }
            if ( file.isDirectory() )
            {
                generateSiteDirectory( mergePath( relativeDirectory, file.getName() ) );
            }
            else if ( file.getName().endsWith( templateSuffix ) )
            {
                generateSiteFile( relativeDirectory, file.getName() );
            }
            else
            {
                getLog().warn( "Ignore resource " + file + " since it's not a velicity template" );
            }
        }
    }

    private void generateSiteFile( String fileDirectory, String fileName )
    {
        String templatePath = "content/" + mergePath( fileDirectory, fileName );
        File destDirectory = new File( flatsiteOutputDirectory, fileDirectory );
        if ( !destDirectory.isDirectory() )
        {
            getLog().info( "Making directory " + destDirectory );
            destDirectory.mkdirs();
        }
        String htmlFileName = fileName.substring( 0, fileName.length() - templateSuffix.length() ) + ".html";
        File htmlFile = new File( destDirectory, htmlFileName );

        Context context = new VelocityContext();
        context.put( "layout", layout );
        context.put( "pom", project );
        context.put( "title", project.getName() );
        context.put( "description", project.getDescription() );
        context.put( "templatePath", templatePath );
        context.put( "htmlPath", mergePath( fileDirectory, htmlFileName ) );
        context.put( "xmltool", xmltool );
        context.put( "now", new Date() );
        context.put( "dateFormat", DateFormat.getDateInstance() );
        context.put( "timeFormat", DateFormat.getTimeInstance() );
        context.put( "sourceDirectory", flatsiteSourceDirectory.getAbsolutePath() );

        String basedir;
        if ( StringUtils.isEmpty( fileDirectory ) )
        {
            basedir = ".";
        }
        else if ( fileDirectory.indexOf( '/' ) == -1 )
        {
            basedir = "..";
        }
        else
        {
            int levels = StringUtils.countMatches( fileDirectory, "/" );
            basedir = ".." + StringUtils.repeat( "/..", levels );
        }

        context.put( "basedir", basedir );

        try
        {
            StringWriter bodyWriter = new StringWriter();
            velocityEngine.mergeTemplate( templatePath, context, bodyWriter );

            String layoutTemplatePath = "layout/" + (String) context.get( "layout" );
            String body = bodyWriter.toString();
            context.put( "body", body );

            getLog().info(
                           "Generating " + htmlFile + " from template " + templatePath + " with layout "
                               + layoutTemplatePath );
            FileWriter output = new FileWriter( htmlFile );
            velocityEngine.mergeTemplate( layoutTemplatePath, context, output );
            output.flush();
            output.close();
        }
        // Exception doesn't stop the transformation process
        catch ( Exception e )
        {
            getLog().warn( "Generating html file " + htmlFile + " failed", e );
        }
    }

    private String mergePath( String relativePath, String fileName )
    {
        return StringUtils.isEmpty( relativePath ) ? fileName : relativePath + "/" + fileName;
    }
}