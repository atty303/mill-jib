> [!IMPORTANT]
> This plugin has reached the end of its useful life with the release of Mill 1.0. I have migrated to [mill-contrib-docker](https://mill-build.org/mill/contrib/docker.html), and recommend using it instead.
> Alternatively, [mill-docker](https://github.com/GeorgOfenbeck/mill-docker) is another option worth considering.

# mill-jib

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.atty303/mill-jib_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.atty303/mill-jib_2.13)

Dockerize java applications on mill builds.

## Usage

Add dependency and import JibModule class in build.sc:

```
import $ivy.`io.github.atty303::mill-jib:0.4.0`
import io.github.atty303.mill.jib.JibModule


```

## JitPack

https://jitpack.io/#atty303/mill-jib
