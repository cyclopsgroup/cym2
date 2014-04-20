package org.cyclopsgroup.cym2.s3upload;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * @author <a href="mailto:jiaqi.guo@gmail.com">Jiaqi Guo</a>
 * @description Upload files in predefined filesets to S3 server
 * @goal upload
 */
public class UploadMojo
    extends AbstractMojo
{
    /**
     * @inheritDoc
     */
    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
    }
}
