package org.cyclopsgroup.cym2.flatsite;

public class PageLinkTool
{
    public static final String TOOL_NAME = "link";

    public class Link
    {
        private final String absolutePath;

        private Link( String path )
        {
            this.absolutePath = path;
        }

        public String toString()
        {
            return baseDir + absolutePath;
        }
    }

    private final String baseDir;

    PageLinkTool( String baseDir )
    {
        this.baseDir = baseDir;
    }

    public Link absolute( String path )
    {
        if ( !path.startsWith( "/" ) )
        {
            path = "/" + path;
        }
        return new Link( path );
    }
}
