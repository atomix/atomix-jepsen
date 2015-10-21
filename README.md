# atomix-jepsen

[Atomix][atomix] [Jepsen][jepsen] tests.

## Overview

atomix-jepsen is a suite of [Jepsen][jepsen] based tests for [Atomix][atomix] including:

* Linearizable CAS

The tests are run against various nemeses including:

* Partition with random halves
* Partition a random isolated node
* Partition in half with a bridge node
* Crash a random set of nodes
* Randomize the clock on all nodes

## Setup

To run atomix-jepsen you'll need to setup a Jepsen test environment. If you don't already have one, you can create one using Docker. First, clone atomix-jepsen:

```
git clone https://github.com/atomix/atomix-jepsen.git
```

Then create a jepsen Docker container, sharing your `atomix-jepsen` directory into the container:

```
cd atomix-jepsen
docker run --privileged --name jepsen -it -v $(pwd):/atomix-jepsen jhalterman/jepsen
```
This jepsen container will include 5 [docker-in-docker](https://github.com/jpetazzo/dind) sub-containers in which Atomix will be deployed.

## Usage

To run the atomix-jepsen tests, from your `atomix-jepsen` directory, run:

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

To use Atomix from your local `.m2` repo instead of pulling and building it from Github, run as:

```
DEV=true lein test
```

## License

Copyright © 2015 Jonathan Halterman

Distributed under the Eclipse Public License version 1.0

[atomix]: https://github.com/atomix/atomix
[jepsen]: https://github.com/aphyr/jepsen