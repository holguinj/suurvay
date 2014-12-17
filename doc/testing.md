# Running Suurvay's Test Suite

Because Suurvay depends heavily on Twitter, you'll need to make credentials available in order to run the automated tests. The tests expect to find credentials in the environment (using the [lein-environ plugin](https://github.com/weavejester/environ)) under `:test-twitter-creds` in the following format:

```clojure
:test-twitter-creds  {:consumer-key "illnevertell"
                      :consumer-secret "youwhatmy"
                      :access-token "secretcredentials"
                      :access-secret "reallyare"}
```

Once your credentials are available, you can use `lein test :all` to run the tests.
