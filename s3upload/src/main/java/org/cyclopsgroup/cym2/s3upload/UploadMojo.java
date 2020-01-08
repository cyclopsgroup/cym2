package org.cyclopsgroup.cym2.s3upload;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
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

@Mojo(name = "upload")
public class UploadMojo extends AbstractMojo {
  @Parameter private List<FileSet> fileSets;

  @Parameter private String bucket;

  @Component private Settings settings;

  @Parameter private String server;

  @Parameter private String awsAccessKeyId;

  @Parameter private String awsSecretKey;

  @Parameter private boolean instanceProfileUsed;

  private class SelfDefinedCredentials implements AWSCredentialsProvider {
    @Override
    public AWSCredentials getCredentials() {
      if (StringUtils.isNotBlank(awsAccessKeyId) && StringUtils.isNotBlank(awsSecretKey)) {
        return new BasicAWSCredentials(awsAccessKeyId, awsSecretKey);
      }
      if (StringUtils.isNotBlank(server)) {
        Server s = settings.getServer(server);
        if (s == null) {
          throw new IllegalStateException("Server " + server + " is not defined in settings.xml");
        }
        if (StringUtils.isNotBlank(s.getUsername()) && StringUtils.isNotBlank(s.getPassword())) {
          return new BasicAWSCredentials(s.getUsername(), s.getPassword());
        }
      }
      throw new IllegalStateException("AWS credentials is not defined in plugin or settings.xml.");
    }

    @Override
    public void refresh() {}
  }

  /** @inheritDoc */
  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    AWSCredentialsProvider creds =
        new AWSCredentialsProviderChain(
            new EnvironmentVariableCredentialsProvider(),
            new InstanceProfileCredentialsProvider(true),
            new SelfDefinedCredentials());
    AmazonS3 s3 =
        AmazonS3ClientBuilder.standard()
            .withRegion(Regions.US_EAST_1)
            .withCredentials(creds)
            .build();
    FileSetManager fileSetManager = new FileSetManager(getLog());
    for (FileSet fs : fileSets) {
      String destPath = StringUtils.trimToEmpty(fs.getOutputDirectory());
      if (StringUtils.isNotBlank(destPath) && !destPath.endsWith("/")) {
        destPath += "/";
      }
      for (String file : fileSetManager.getIncludedFiles(fs)) {
        File source = new File(fs.getDirectory(), file);

        getLog().info("Uploading file " + source + " to s3://" + bucket + "/" + destPath + file);
        s3.putObject(bucket, destPath + file, source);
      }
    }
  }
}
