# copycat-jepsen

[Copycat][copycat] [Jepsen][jepsen] tests.

## Overview

copycat-jepsen is a suite of [Jepsen][jepsen] based tests for [Copycat][copycat] including:

* Linearizable counter
* Linearizable CAS

## Setup

* Check out the [Jepsen setup instructions](https://github.com/aphyr/jepsen/tree/master/jepsen) or use [jepsen-vagrant](https://github.com/abailly/jepsen-vagrant) to quickly setup a Jepsen environment.
* Make sure that Jepsen and Copycat are both installed in your local maven repo.

#### Extra Setup

To cut down on container disk usage, you can link your host's maven repo into your containers:

```
for i in 1 2 3 4 5; do ln -s ~/.m2/repository /var/lib/lxc/n${i}/rootfs/root/.m2; done
```

If your jepsen host is a VM, you can also share your VM host's maven repo with it. For Vagrant you can add the config:

```
config.vm.synced_folder "~/.m2/repository", "/home/vagrant/.m2/repository"
```

## Usage

From your jepsen host's `copycat-jepsen` directory:

```
lein test
```

## Notes

If you need to kill the copycat processes, from your jepsen host:

`for i in 1 2 3 4 5; do sudo lxc-attach -n n${i} -- pkill java; done`

## License

Copyright © 2015 Jonathan Halterman

Distributed under the Eclipse Public License version 1.0

[copycat]: https://github.com/kuujo/copycat
[jepsen]: https://github.com/aphyr/jepsen