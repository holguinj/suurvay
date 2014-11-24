# suurvay

A Clojure library for building Twitter autoblocker bots.

## Features

* Relatively simple, data-first approach to assessing users without wasting precious Twitter API calls (which are heavily rate-limited).
* Gathers as much information as possible from the Twitter streaming API, falling back on the REST API for additional information when necessary.
* Attempts to handle over-capacity and rate-limit errors transparently.

## Usage

Upcoming.

* Constructing a streaming channel
* Constructing a test map
* Taking action

### Namespaces

* `suurvay.identification`: contains the `test-runner` function that does most of the hard work of this library, along with a few pure helper functions for constructing generic rules.
* `suurvay.twitter`: contains pure functions and predicates that are useful when processing data during tests. Also contains side-effecting functions that interact with the Twitter API, but you should avoid those if possible.
* `suurvay.streaming-twitter`: contains the `filter-stream` function, which opens a Twitter search stream and places the results onto a core.async channel that the caller supplies.
* `suurvay.training`: contains functions that make heavy use of the Twitter REST API to gather information about groups of Twitter users, including popular hashtags and profile sentiments (pro-X, anti-Y, etc.).


# Name

Suur Vay is a minor character in Neal Stephenson's [Anathem](http://en.wikipedia.org/wiki/Anathem). She's a sort of philosopher-warrior who renders medical aid to one of the main characters. That plus the homophony with *survey* made it seem like an appropriate choice.

## License

Copyright © 2014 Justin Holguin

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
