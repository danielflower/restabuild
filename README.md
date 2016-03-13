Restabuild
----------

Your self-hosted build server that let's you quickly build projects from a RESTful interface only.
Simply place `build.sh` (or `build.bat` if the build server is Windows) into the root of your project
and then `POST` to `/build?gitUrl={your-git-ssh-or-http-url}`

Running locally
---------------

Run `com.danielflower.restabuild.RunLocal.main` from your IDE. This will use the settings in
`sample-config.properties`.

Configuration
-------------

See `sample-config.properties` for configuration information. Each setting can be specified
as an environment variable, a java system property, or in a properties file who's path is
specified as a command line argument.