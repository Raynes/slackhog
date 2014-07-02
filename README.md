# slackhog

A Clojure program for backing up your slack history. Supports channels, groups,
and ims, as well as users and channels themselves (mappings of ids to names).

## Usage

```
lein uberjar
```

This will create a jar that we can run.

If this is the first time you're running, you should create the tables slackhog
looks for. These are the `users`, `channels`, and `messages` tables. Create them
using the sql files in `sql/`.

```
export SUBNAME='//localhost:5432/slackhog'
export PGUSER=user
export PGPASSWORD=pass
export SLACK_TOKEN=token

java -jar target/slackhog.jar channels groups ims channel-ids user-ids
```

Some notes:

* `SUBNAME` defaults to the above. It was included merely as an example of hor
to change this.
* `PGUSER` and `PGPASS` default to no authentication.

### Things to backup

* `channels`: Messages from all public channels.
* `groups`: Messages from all private groups you're a part of.
* `ims`: Messages from all private IM convos you're a part of.
* `user-ids`: Populate the users table.
* `channel-ids`: Populate the channels table.

## License

Copyright © 2014 Anthony Grimes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
