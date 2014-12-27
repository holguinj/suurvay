# Suurvay

A Clojure library for building Twitter autoblocker bots.

## Features

* Relatively simple, data-first approach to assessing users without wasting precious Twitter API calls (which are heavily rate-limited).
* Gathers as much information as possible from the Twitter streaming API, falling back on the REST API for additional information when necessary.
* Attempts to handle over-capacity and rate-limit errors transparently.

## Usage

### Overview

Running a bot involves a few steps:

1. Construct a test map for use with `score-user`.
2. Initialize a core.async channel with a stream of tweets.
3. Start a loop to pass tweets from the channel to `score-user`, blocking users who pass a given threshold.

### Construct a Test Map

The test map is at the heart of Suurvay. Twitter's rate limits make some forms of data much more expensive than others, Suurvay has a very opinionated order for making these calls, with the hope of making a decision early on and avoiding the later, more expensive calls.

Here's the basic form of the test map:

```clojure
{:limit      Number
 :before     (Status   -> score)
 :real-name  (RealName -> score)
 :profile    (User     -> score)
 :timeline   ([Status] -> score)
 :friends    ([ID]     -> score)
 :followers  ([ID]     -> score)}
```

I'll break down the keys one by one:

* `:limit`: The tests will be run until this number is met or exceeded by the cumulative score. At that point, the cumulative score will be returned. Your goal should be to hit the limit as soon as possible to avoid getting rate-limited by the API.
* `:before`: The given function will be called with the complete, unmodified status object from the streaming API. You can use this opportunity to check a user whitelist, log the status, or take care of whatever else you might like before starting the actual classification. Returning a number larger than `:limit` at this point will stop the classification, as will returning a negative number.
* `:real-name`: This function will be called with the user's "real name," which is the longer, easily changeable name (not the "@screen_name").
* `:profile`: This function will be called with the full user object associated with the given tweet. Check out the `:description` key for the text of the profile itself. This is the last function that gets called using only the data from the streaming API; the following keys all use the REST API and are therefor subject to rate limiting.
* `:timeline`: This function will be called with a vector of up to 200 status objects from the user's timeline. Each has a `:text` key that contains the body of the tweet. Twitter will allow you to make up to 300 of these calls per 15-minute period, so it's fairly safe to wait until this point to make a decision.
* `:friends`: This function will be called with a vector of up to 5,000 user IDs that the given user is following. You can only make 15 of these calls per 15-minute period, so I recommend against using this key at all if you can possibly avoid it.
* `:followers`: This function will be called with a vector of up to 5,000 user IDs that are following the given user. The same caveat applies here as with `:friends`.

>For more detailed information, see [writing test maps](doc/writing_test_maps.md).

### Initialize a Streaming Tweet Channel

Although you can technically call `score-user` however you like, Suurvay is written with core.async in mind. Here's an example of how to create a channel and direct streaming tweets to it.

```clojure
(ns suurvay.example
  (:require [suurvay.streaming-twitter :refer [filter-stream]]
            [suurvay.identification :refer [score-user]]
            [clojure.core.async :refer [chan <!! close!]]
            [clojure.pprint :refer [pprint]]))

;; You'll need credentials for the Twitter API
(def creds
  {:consumer-key "blahblah"
   :consumer-secret "blahblah"
   :access-token "blahblah"
   :access-secret "blahblah"})

;; and a core.async channel to hold the tweets from the search
(def tweet-channel (chan))

;; here's how to actually begin the stream
(def stream
  (filter-stream creds
                 tweet-channel
                 ["gamergate" "harassment"])) ;; search terms

;; cancel the stream and close the channel when you're done (optional)
(defn end-stream! []
  (deliver (:done stream) true)
  (close! tweet-channel))
```

The above code doesn't have any visible effect, but if you take from `tweet-channel` then you should find a status object:

```clojure
(pprint (<!! tweet-channel))
```

### Begin the Classification Loop

Here's a basic test map that just prints the tweet body:

```clojure
(def test-map
  {:limit 1
   :before (fn [status]
             (pprint status)
             -1)}) ;; note that you must return a number!
```

Now we have everything we need to start the main loop:

```clojure
(while true
  (let [tweet (<!! tweet-channel)
        score (score-user test-map tweet)]
    (when (< 1 score)
      (println "Blocking this jerk!")))) ;; don't just print it, do it.
```

## Development

### Namespaces
* `suurvay.identification`: contains the `score-user` function that does most of the hard work of this library, along with a few pure helper functions for constructing generic rules.
* `suurvay.streaming-twitter`: contains the `filter-stream` function, which opens a Twitter search stream and places the results onto a core.async channel that the caller supplies.
* `suurvay.training`: contains functions that make heavy use of the Twitter REST API to gather information about groups of Twitter users, including popular hashtags and profile sentiments (pro-X, anti-Y, etc.).
* `suurvay.twitter-pure`: contains pure functions and predicates that are useful when processing data during identification.
* `suurvay.twitter-rest`: contains functions that interact directly with the Twitter API. It's usually not necessary to call these directly as `suurvay.identification/score-user` will do it for you. The exception here is `block!`, which you must call manually.
* `suurvay.annotations`: contains [core.typed](https://github.com/clojure/core.typed/) annotations for the types and functions in this project. Note that none of this code makes use of the annotations yet -- they're included strictly as an aid for those who would like to use this library in a typed Clojure project.

# Name

Suur Vay is a minor character in Neal Stephenson's [Anathem](http://en.wikipedia.org/wiki/Anathem). She's a sort of philosopher-warrior who renders medical aid to one of the main characters. That plus the homophony with *survey* made it seem like an appropriate choice.

## License

Copyright © 2014 Justin Holguin

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
