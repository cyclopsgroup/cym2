package org.cyclopsgroup.cym2.s3upload;

import java.io.File;
import java.util.List;

import org.apache.commons.lang.StringUtils;
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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.internal.StaticCredentialsProvider;
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

    @Parameter
    private String awsAccessKeyId;

    @Parameter
    private String awsSecretKey;

    @Parameter
    private boolean instanceProfileUsed;

    /**
     * @inheritDoc
     */
    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        AWSCredentialsProvider creds;
        if ( StringUtils.isNotBlank( awsAccessKeyId )
            && StringUtils.isNotBlank( awsSecretKey ) )
        {
            creds =
                new StaticCredentialsProvider(
                                               new BasicAWSCredentials(
                                                                        awsAccessKeyId,
                                                                        awsSecretKey ) );
        }
        else if ( instanceProfileUsed )
        {
            creds = new InstanceProfileCredentialsProvider();
        }
        else if ( StringUtils.isNotBlank( server ) )
        {
            Server s = settings.getServer( server );
            creds =
                new StaticCredentialsProvider(
                                               new BasicAWSCredentials(
                                                                        s.getUsername(),
                                                                        s.getPassword() ) );
        }
        else
        {
            throw new MojoFailureException(
                                            "Must specify awsAccessKeyId+awsSecretKey, server or instanceProfileUsed to provide AWS credentials" );
        }
        AmazonS3 s3 = new AmazonS3Client( creds );
        FileSetManager fileSetManager = new FileSetManager( getLog() );
        for ( FileSet fs : fileSets )
        {
            String destPath = StringUtils.trimToEmpty( fs.getOutputDirectory() );
            if ( StringUtils.isNotBlank( destPath ) && !destPath.endsWith( "/" ) )
            {
                destPath += "/";
            }
            for ( String file : fileSetManager.getIncludedFiles( fs ) )
            {
                File source = new File( fs.getDirectory(), file );

                getLog().info( "Uploading file " + source + " to s3://"
                                   + bucket + ":" + destPath + file );
                s3.putObject( bucket, destPath + file, source );
            }
        }
    }
}
