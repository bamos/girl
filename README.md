# GitHub README Link Checker
[![Build Status](https://travis-ci.org/bamos/girl.svg?branch=master)](https://travis-ci.org/bamos/girl)

<!-- ![](https://raw.githubusercontent.com/bamos/girl/master/screenshot.png) -->

`girl` is a <b>Gi</b>thub <b>R</b>eadme <b>L</b>ink Checker
served over HTTP with [Scala](http://scala-lang.org/)
and [Spray](http://spray.io/).
A public version is hosted at <http://bamos.github.io/girl>.

## Broken Links in Top GitHub Repos
<http://derecho.elijah.cs.cmu.edu:8585/@top> checks broken links in
the [top 1000 GitHub repositories](https://github.com/search?q=stars%3A%3E1)
by number of stars.
This project found 416 broken links (with some false positives) when this
feature was released on 2015/03/12.
See the page's history on the
[Wayback Machine](https://web.archive.org/web/20150313120353/http://derecho.elijah.cs.cmu.edu:8585/@top).

<!-- ![](https://raw.githubusercontent.com/bamos/girl/master/top-screenshot-2015-03-12.png) -->

## Whitelist
To prevent misuse, girl restricts usage to
GitHub users with
over 50 followers or users and organizations on the
[whitelist](https://github.com/bamos/girl/blob/master/src/main/scala/Whitelist.scala).
Please add your accounts to the
[whitelist](https://github.com/bamos/girl/blob/master/src/main/scala/Whitelist.scala)
and submit a pull request to gain access. Thanks!

## Building

Before running locally,
set your [GitHub API token](https://github.com/blog/1509-personal-api-tokens)
in the environment variable `GITHUB_TOKEN`,
or modify the GitHub API connection in
[Girl.scala](https://github.com/bamos/girl/blob/master/src/main/scala/Girl.scala)
to another option from
[kohsuke.github.GitHub](http://github-api.kohsuke.org/apidocs/org/kohsuke/github/GitHub.html).
Also in `Girl.scala`, if desired, set the minimum number of
required followers to zero: `val reqFollowers = 0`.


`girl` is built with [sbt][sbt].
Executing `sbt run` from the `girl` directory will download
the dependencies, compile the source code, and start
an HTTP server on `0.0.0.0:8585`.
[Main.scala](https://github.com/bamos/girl/blob/master/src/main/scala/Main.scala)
configures the interface and port.

[sbt-revolver][sbt-revolver] is helpful for development.
Start an `sbt` shell and execute `~re-start`,
which re-compiles and restarts the server upon source code changes.

[sbt]: http://www.scala-sbt.org/
[sbt-revolver]: https://github.com/spray/sbt-revolver

## Deployment with Docker

Girl can be deployed as a container with [Docker](https://www.docker.com/).
After replacing the string `<token>` in the
[Dockerfile](https://github.com/bamos/girl/blob/master/Dockerfile)
with your GitHub API token, the following command
will build and start the girl as an HTTP server on port 8585
of the container.

```
docker build -t girl .
```

## Running as a System Service
[girl.service](https://github.com/bamos/girl/blob/master/girl.service)
is an example [systemd](http://www.freedesktop.org/wiki/Software/systemd/)
service that calls
[start-service.sh](https://github.com/bamos/girl/blob/master/start-service.sh)
to automatically start girl with the system.

Modify the paths to this repo on your system in both of the scripts
and copy `girl.service` to `/etc/systemd/system/girl.service`.
A symlink will not work, see
[this bug report](https://bugzilla.redhat.com/show_bug.cgi?id=955379)
for more details.

Basic controls are:

```
sudo systemctl start girl
sudo systemctl stop girl
sudo systemctl restart girl
```

And run on startup with:

```
sudo systemctl enable girl
```

## Licensing
All portions are [MIT-licensed](https://github.com/bamos/girl/blob/master/LICENSE.mit).
