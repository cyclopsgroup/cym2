package org.cyclopsgroup.cym2.awss3;

import org.apache.maven.wagon.StreamingWagonTestCase;
import org.junit.Ignore;

/**
 * Test case of S3 wagon which doesn't work since it runs against real S3 service
 *
 * @author <a href="mailto:jiaqi@cyclopsgroup.org">Jiaqi Guo</a>
 */
@Ignore
public class S3WagonTest
    extends StreamingWagonTestCase
{
    /**
     * @inheritDoc
     */
    @Override
    protected String getProtocol()
    {
        return "s3";
    }

    @Override
    protected int getTestRepositoryPort()
    {
        return 443;
    }

    /**
     * @inheritDoc
     */
    @Override
    protected String getTestRepositoryUrl()
    {
        return "s3://test-bucket/testpath/root";
    }
}
