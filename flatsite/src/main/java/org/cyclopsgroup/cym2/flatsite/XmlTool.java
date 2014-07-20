package org.cyclopsgroup.cym2.flatsite;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;

/**
 * A utility for XML manipulation using Dom4j internally
 *
 * @author <a href="mailto:jiaqi.guo@gmail.com">Jiaqi Guo</a>
 */
public class XmlTool
{
    private final File resourceRoot;

    XmlTool( File resourceRoot )
    {
        this.resourceRoot = resourceRoot;
    }

    private SAXReader saxReader = new SAXReader();

    /**
     * Split big list into list of small site-limited lists
     *
     * @param <T> Type of element
     * @param list Input big list
     * @param groupSize Size of small list(group)
     * @return List of groups
     */
    public <T> List<List<T>> groupBy( List<T> list, int groupSize )
    {
        List<List<T>> groups =
            new ArrayList<List<T>>( list.size() / groupSize + 1 );
        for ( int index = 0; index < list.size(); index++ )
        {
            List<T> group;
            if ( index % groupSize == 0 )
            {
                group = new ArrayList<T>( groupSize );
                groups.add( group );
            }
            else
            {
                group = groups.get( index / groupSize );
            }
            group.add( list.get( index ) );
        }

        return groups;
    }

    /**
     * Parse a File into Dom4j Document
     *
     * @param filePath Path of file
     * @return Dom4j document object
     * @throws DocumentException
     */
    public Document parseFile( String filePath )
        throws DocumentException
    {
        return saxReader.read( new File( resourceRoot, filePath ) );
    }

    /**
     * Parse a URL into Dom4j Document
     *
     * @param urlString URL of resource
     * @return Dom4j document
     * @throws DocumentException
     * @throws MalformedURLException
     */
    public Document parseUrl( String urlString )
        throws DocumentException, MalformedURLException
    {
        return saxReader.read( new URL( urlString ) );
    }
}