package org.cyclopsgroup.cym2.awss3;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Utility to parse /etc/mime.types file and generate properties file
 *
 * @author <a href="mailto:jiaqi@cyclopsgroup.org">Jiaqi Guo</a>
 */
public class MimeTypesParser
{
    private static final String DEFAULT_MIME_TYPES = "/etc/mime.types";

    private Properties parse( File mimeTypes )
        throws IOException
    {
        Properties props = new Properties();
        LineNumberReader in = new LineNumberReader( new FileReader( mimeTypes ) );
        try
        {
            for ( String line = in.readLine(); line != null; line = in.readLine() )
            {
                String ln = StringUtils.trimToNull( line );
                if ( ln == null || ln.startsWith( "#" ) )
                {
                    continue;
                }
                String[] chunks = StringUtils.split( ln, '\t' );
                if ( chunks.length < 2 )
                {
                    continue;
                }
                String mimeType = chunks[0];
                String[] extensions = StringUtils.split( chunks[1], ' ' );
                for ( String extension : extensions )
                {
                    props.setProperty( extension, mimeType );
                }
            }
            return props;
        }
        finally
        {
            IOUtils.closeQuietly( in );
        }
    }

    private Properties parse()
        throws IOException
    {
        return parse( new File( DEFAULT_MIME_TYPES ) );
    }

    /**
     * @param args Command line arguments
     * @throws IOException Allows IO errors
     */
    public static void main( String[] args )
        throws IOException
    {
        System.out.println( "Generating mimetypes from " + DEFAULT_MIME_TYPES );
        if ( args.length > 1 )
        {
            throw new IllegalArgumentException( "Invalid arguments " + Arrays.toString( args ) );
        }
        Properties props = new MimeTypesParser().parse();
        if ( args.length == 0 )
        {
            System.out.println( props );
            return;
        }
        File out = new File( args[0] );
        out.getParentFile().mkdirs();
        FileWriter o = new FileWriter( out );
        try
        {
            props.store( o, "Generated at " + new Date() );
            o.flush();
        }
        finally
        {
            IOUtils.closeQuietly( o );
        }
    }
}
