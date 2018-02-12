Restabuild
----------

Your self-hosted build server that let's you quickly build projects from a RESTful interface only.
Simply place `build.sh` (or `build.bat` if the build server is Windows) into the root of your project
and then `POST` to `/restabuild/api/v1/builds` with `gitUrl` as a form parameter. The web UI gives
more detail on the API along with sample curl commands.

**GOTCHA:** The `build.sh` file needs to be committed to git as executable. If it is not, you can
mark it as executable with `git update-index --chmod=+x build.sh`

Running locally
---------------

Run `com.danielflower.restabuild.RunLocal.main` from your IDE. This will use the settings in
`sample-config.properties` and give a link to open the UI.

Configuration
-------------

See `sample-config.properties` for configuration information. Each setting can be specified
as an environment variable, a java system property, or in a properties file who's path is
specified as a command line argument.

Running
-------

1. [Download the latest jar](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.danielflower.apprunner%22%20AND%20a%3A%22restabuild%22)
2. Create a config file (e.g. by copying `sample-config.properties`) and then execute the jar:

       java -jar {path-to-jar} {path-to-config}
