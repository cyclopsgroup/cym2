package org.cyclopsgroup.cym2.s3upload;

import java.io.File;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

@Mojo( name = "upload" )
public class UploadMojo
    extends AbstractMojo
{
    @Parameter
    private List<FileSet> fileSets;

    @Parameter
    private String bucket;

    @Component
    private Settings settings;

    @Parameter
    private String server;

    /**
     * @inheritDoc
     */
    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        Server s = settings.getServer( server );
        AmazonS3 s3 =
            new AmazonS3Client( new BasicAWSCredentials( s.getUsername(),
                                                         s.getPassword() ) );
        FileSetManager fileSetManager = new FileSetManager( getLog() );
        for ( FileSet fs : fileSets )
        {
            String destPath = fs.getOutputDirectory();
            for ( String file : fileSetManager.getIncludedFiles( fs ) )
            {
                File source = new File( fs.getDirectory(), file );
                s3.putObject( bucket, destPath + file, source );
            }
        }
    }
}
