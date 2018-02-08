# What is it?

Despite the confusing project name, which doesn't actually mean anything, CYM2 is a small set of Maven extensions that I have developed during time to serve other OSS projects.

# Maven plugins

## s3upload - Maven s3upload Plugin
A plugin that uploads arbitrary files in project to AWS S3. I often upload website static contents to CloudFront with this plugin.

## uberjar - Maven uberjar Plugin
This plugin was created long time ago to build [uberjar](http://classworlds.codehaus.org/uberjar.html) artifact easily. However the same result can be achieved decently with Maven Assembly Plugin so I haven't touched it for a while.

## flatsite - Maven Flatsite Plugin
Flatsite is a Maven plugin that generates site using customized [Velocity](http://velocity.apache.org) template. This plugin gives user full control of template and allows to create arbitrary structure of website. As a trade off, it can't be used to generate standard Maven reports.

# Maven extension

## awss3, AWS S3 Maven wagon
This extension allows user to upload artifact or generated Maven site to Amazon S3. There are other open source projects out there for the same purpose, but at the time I was looking for such extension, none of the existing providers upload Maven site correctly, they all seem to target artifact upload. And surprisingly, none of them is implemented with AWS Java SDK, thus complicated.

The CYM2 version of AWS S3 wagon takes advantage of AWS Java SDK, has literally nothing but two short classes and does everything I need. It works well for all my other open source projects so far so I can't see a reason not to open source it.

Check out [this blog](http://blog.cyclopsgroup.org/2011/06/publish-maven-site-with-amazon-s3-and.html) to learn a little bit more.
