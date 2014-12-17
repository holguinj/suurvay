# Writing Test Maps

Most of the building a bot around Suurvay is likely to come from writing the test map for `suurvay.identification/score-user`. This page gives a more detailed overview of each key in the test map, the data it is expected to receive, and how best to make use of it.

## Format

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

The only required key here is `:limit`, but nothing will actually happen unless you have at least one of the functions defined. Note that functions may be absent, but otherwise **they must return a number**. It doesn't matter if it's positive, negative, float, integer, or rational, but it must be a number.

The `score-user` function is guaranteed to run the tests in the order above. It will stop running tests and return the cumulative score when one of the following happens:

1. the cumulative score reaches/exceeds the limit
2. the cumulative score drops below 0
3. it completes all available tests.

It is in your best interest to see that one of these happens as quickly as is reasonably possible -- preferably before the `:timeline` test and almost certainly before `:friends`. Otherwise, you run the real risk of hitting Twitter's rate limit, which can pause your bot for up to 15 minutes at a time and possibly cause your streaming connection to fall behind and drop.

## `:limit`

The `:limit` key is not a function, but it's a part of the map and must not be omitted. The limit can be any real number; all that really matters is that it be in some kind of reasonable proportion to the numbers returned by your scoring functions. For example, if you set `:limit 100` then your scoring functions should probably be returning numbers in the range of 0-100.

Remember, your goal is to make a positive or negative match as quickly as you justifiably can.

## `:before`

The `:before` key is just a little bit of a hack. It contains all of the data exactly as it originally arrived from the Twitter streaming API. You can provide a function here for debugging, logging, checking a list of known users, etc. Just remember that your `:before` function, if present, *must return a number*.

>The argument to your `:before` function will be a [Twitter Status object](https://dev.twitter.com/overview/api/tweets) with `:keywordized_keys`.

## `:real-name`

The `:real-name` function is used to evaluate the user in question's long-form chosen name. This is distinct from the user's @handle. Another good term for it might be "display name," but Twitter consistently calls it the "real name." I included this step because I noticed that some enthusiastic members of hate groups will actually advertise it in their Twitter names.

>The argument to your `:real-name` function will be a string.

## `:profile`

The `:profile` function takes the remainder of the information provided about the user by the Twitter streaming API, so it's a really good place to finish making a decision about a user before using any additional Twitter API calls and risking hitting the rate limit. This function will have access to the profile text (under the `:description` key), the creation date of the account (`:created_at`), and other really useful tidbits.

>The argument to your `:profile` function will be a [Twitter User object](https://dev.twitter.com/overview/api/users) with `:keywordized_keys`.

## `:timeline`

The `:timeline` function takes the most recent 200 tweets associated with the user in question. Each tweet is represented as a map with the `:text` of the tweet, along with additional information like whether it's a `:retweeted` status. Getting this information burns a REST API call, and the endpoint is limited to up to 300 calls per 15-minute period. That sounds like a lot, but you can easily hit it within 2 or 3 minutes if you're collecting a lot of tweets and not making decisions quickly enough.

>The argument to your `:timeline` function will be a vector of [Twitter Status objects](https://dev.twitter.com/overview/api/tweets) with `:keywordized_keys`.

## `:friends` and `:followers`

The `:friends` and `:followers` functions take a vector of user IDs that the user in question is *following* and *followed by*, respectively. These calls are **heavily rate-limited** and **should be avoided** if at all possible. You can only check them a *combined* 15 times per 15-minute period, so checking `:friends` counts against your limit for checking `:followers` and vice-versa.

Note that only the first 5,000 user IDs will be retrieved.

>The argument to your `:friends` and `:followers` functions will be a vector of user IDs (integers).
