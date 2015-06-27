# copycat-jepsen

[Copycat][copycat] [Jepsen][jepsen] tests.

## Overview

copycat-jepsen is a suite of [Jepsen][jepsen] based tests for [Copycat][copycat] including:

* Linearizable read
* Linearizable write
* Linearizable CAS

The tests are run against various nemeses including:

* Partition with random halves
* Partition a random node
* Partition in half with a bridge node
* Crash a random minority of nodes

## Setup

To run copycat-jepsen you'll need to setup a Jepsen test environment. If you don't already have one, you can create one using Docker. First, clone copycat-jepsen:

```
git clone https://github.com/jhalterman/copycat-jepsen.git
```

Then create a jepsen Docker container, sharing your `copycat-jepsen` directory into the container:

```
cd copycat-jepsen
docker run --privileged --name jepsen -it -v $(pwd):/copycat-jepsen jhalterman/jepsen
```
This jepsen container will include 5 [docker-in-docker](https://github.com/jpetazzo/dind) sub-containers in which Copycat will be deployed.

## Usage

To run the copycat-jepsen tests, from your `copycat-jepsen` directory, run:

```
lein test
```

## Notes

#### Shared Repository

To cut down on test setup time and disk usage, you can share your local `~/.m2` directory with your Jepsen environment by including the following in your `docker run` command:

```
-v $HOME/.m2:/root/.m2
```

#### Skip Build

To run Copycat from your local `.m2` repo instead of pulling and building it from Github, run as:

```
DEV=true lein test
```

## License

Copyright © 2015 Jonathan Halterman

Distributed under the Eclipse Public License version 1.0

[copycat]: https://github.com/kuujo/copycat
[jepsen]: https://github.com/aphyr/jepsen