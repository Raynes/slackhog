# slackhog

A Clojure program for backing up your slack history. Supports channels, groups,
and ims.

## Usage

```
lein uberjar
```

This will create a jar that we can run.

```
export SUBNAME='//localhost:5432/slackhog'
export PGUSER=user
export PGPASSWORD=pass
export SLACK_TOKEN=token

java -jar target/slackhog.jar channels groups im
```

Some notes:

* `SUBNAME` defaults to the above. It was included merely as an example of hor
to change this.
* `PGUSER` and `PGPASSWORD` default to no authentication.

The arguments to the program are the kinds of things to fetch. This is any
combination of `channels`, `groups`, and `im`. Note that `im` is inconsistently
non-plural because Slack's API is inconsistently pluralized. :P

## License

Copyright © 2014 Anthony Grimes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
