package org.cyclopsgroup.cym2.protoc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo( name = "generate" )
public class ProtocGenerateMojo
    extends AbstractMojo
{
    @Parameter( defaultValue = "protoc" )
    private String protocExecutable;

    @Parameter( defaultValue = "${project.basedir}/target/generated-sources/proto" )
    private String outputDirectory;

    @Parameter( defaultValue = "${project.basedir}/src/main/proto" )
    private String inputDirectory;

    /**
     * @inheritDoc
     */
    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        File inputDir = new File( inputDirectory );
        if ( !inputDir.isDirectory() )
        {
            getLog().info( "Input directory " + inputDir + " isn't a directory, exit." );
            return;
        }
        @SuppressWarnings( "unchecked" )
        List<File> protoFiles = new ArrayList<File>( FileUtils.listFiles( inputDir, new String[] { "proto" }, false ) );
        if ( protoFiles.isEmpty() )
        {
            getLog().info( "No proto file is found in " + inputDir + ", exit." );
            return;
        }
        List<String> args =
            new ArrayList<String>( Arrays.asList( protocExecutable, "--proto_path=" + inputDir.getAbsolutePath() ) );

        try
        {
            File outputDir = new File( outputDirectory );
            if ( !outputDir.exists() )
            {
                this.getLog().info( "Creating directory " + outputDir + " since it doesn't exist" );
                FileUtils.forceMkdir( outputDir );
            }
            args.add( "--java_out=" + outputDir.getAbsolutePath() );
            for ( File proto : protoFiles )
            {
                args.add( proto.getAbsolutePath() );
            }
            getLog().info( "Running process with arguments: " + args + "..." );
            Process proc = new ProcessBuilder( args.toArray( ArrayUtils.EMPTY_STRING_ARRAY ) ).start();
            proc.waitFor();
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Can't generate proto files: " + e.getMessage(), e );
        }
        catch ( InterruptedException e )
        {
            throw new MojoExecutionException( "Process " + protocExecutable + " is interrupted: " + e.getMessage(), e );
        }
    }
}
