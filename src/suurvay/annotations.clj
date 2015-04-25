(ns suurvay.annotations
  "Contains annotations for functions *outside* of this project."
  (:require [clojure.core.typed :as t :refer [Any Bool Fn HMap HVec
                                              IFn Int Keyword Map Num
                                              Option Seq Str U Val Vec
                                              ann defalias]]
            [suurvay.identification :refer [TwitterAPI]]
            [clojure.core.typed.async :refer [Chan]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Inner (private) data types
(defalias Coordinates
  (HMap
   :mandatory
   {:coordinates (HVec [Num Num])
    :type (Val "Point")}
   :complete? true))

(defalias URLEntity
  (HMap
   :mandatory
   {:url Str
    :expanded_url Str
    :display_url Str
    :indices (Vec Int)}
   :complete? true))

(defalias HashTagEntity
  (HMap
   :mandatory
   {:text Str
    :indices (Vec Int)}
   :complete? true))

(defalias MentionsEntity
  (HMap
   :mandatory
   {:screen_name Str
    :name Str
    :id Int
    :id_str Str
    :indices (Vec Int)}
   :complete? true))

(defalias Entities
  (HMap :mandatory
        {:urls (Vec URLEntity)
         :hashtags (Vec HashTagEntity)
         :user_mentions (Vec MentionsEntity)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Twitter response types
(defalias UserAbbrev
  (HMap
   :mandatory
   {:profile_sidebar_fill_color Str
    :profile_sidebar_border_color Str
    :profile_background_tile Bool
    :name Str
    :profile_image_url Str
    :created_at Str
    :location Str
    :follow_request_sent Bool
    :profile_link_color Str
    :is_translator Bool
    :id_str Str
    :entities Entities,
    :default_profile Bool
    :contributors_enabled Bool
    :favourites_count Int
    :url Str
    :profile_image_url_https Str
    :utc_offset Int
    :id Int
    :profile_use_background_image Bool
    :listed_count Int
    :profile_text_color Str
    :lang Str
    :followers_count Int
    :protected Bool
    :notifications (Option Bool) ;; deprecated
    :profile_background_image_url_https Str
    :profile_background_color Str
    :verified Bool
    :geo_enabled Bool
    :time_zone Str
    :description Str
    :default_profile_image Bool
    :profile_background_image_url Str
    :statuses_count Int
    :friends_count Int
    :following Bool
    :show_all_inline_media Bool
    :screen_name Str}))

(defalias Status
  (HMap
   :mandatory
   {:coordinates (Option Coordinates),
    :favorited Bool
    :truncated Bool
    :created_at Str
    :id_str Str
    :entities Entities,
    :in_reply_to_user_id_str (Option Str)
    :contributors (Vec Int)
    :text Str
    :retweet_count Int
    :in_reply_to_status_id_str (Option Str)
    :id Int
    :geo (Option Coordinates) ;; deprecated
    :retweeted Bool
    :possibly_sensitive Bool
    :in_reply_to_user_id (Option Int)
    :place (Option (Map Keyword Any)) ;; TODO: implement
    :user UserAbbrev,
    :in_reply_to_screen_name (Option Str)
    :source Str
    :in_reply_to_status_id (Option Int)}))

(defalias StatusAbbrev
  (HMap
   :mandatory
   {:coordinates (Option Coordinates)
    :favorited Bool
    :truncated Bool
    :created_at Str
    :retweeted_status (Option (HMap :mandatory
                                    {:coordinates (Option Coordinates)
                                     :favorited Bool
                                     :truncated Bool
                                     :created_at Str
                                     :id_str Str
                                     :entities Entities,
                                     :in_reply_to_user_id_str (Option Str)
                                     :contributors (Option (Vec Any)) ;;whatevs
                                     :text Str
                                     :retweet_count Int
                                     :in_reply_to_status_id_str (Option Str)
                                     :id Int
                                     :geo (Option Any) ;; deprecated
                                     :retweeted Bool
                                     :possibly_sensitive Bool
                                     :in_reply_to_user_id (Option Int)
                                     :place (Option (Map Keyword Any))
                                     :in_reply_to_screen_name (Option Str)
                                     :source Str
                                     :in_reply_to_status_id (Option Int)})),
    :id_str Str
    :entities Entities,
    :in_reply_to_user_id_str (Option Str)
    :contributors (Option Any) ;; no idea
    :text Str
    :retweet_count Int
    :in_reply_to_status_id_str (Option Str)
    :id Int
    :geo (Option (Map Keyword Any)) ;; whatevs
    :retweeted Bool
    :possibly_sensitive Bool
    :in_reply_to_user_id (Option Int)
    :place (Option (Map Keyword Any)) ;; probably not right
    :in_reply_to_screen_name (Option Str)
    :source Str
    :in_reply_to_status_id (Option Int)}))

(defalias User
  (HMap
   :mandatory
   {:profile_sidebar_fill_color Str
    :profile_sidebar_border_color Str
    :profile_background_tile Bool
    :name Str
    :profile_image_url Str
    :created_at Str
    :location Str
    :follow_request_sent Bool
    :profile_link_color Str
    :is_translator Bool
    :id_str Str
    :default_profile Bool
    :contributors_enabled Bool
    :favourites_count Int
    :url (Option Str)
    :profile_image_url_https Str
    :utc_offset Int
    :id Int
    :profile_use_background_image Bool
    :listed_count Int
    :profile_text_color Str
    :lang Str
    :followers_count Int
    :protected Bool
    :notifications Bool
    :profile_background_image_url_https Str
    :profile_background_color Str
    :verified Bool
    :geo_enabled Bool
    :time_zone Str
    :description Str
    :default_profile_image Bool
    :profile_background_image_url Str
    :status StatusAbbrev,
    :statuses_count Int
    :friends_count Int
    :following Bool
    :show_all_inline_media Bool
    :screen_name Str}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Internal types
(defalias Identifier (U Str Int Status User UserAbbrev))

(defalias UserParam (U (HMap :mandatory {:user-id (U Int Str)})
                       (HMap :mandatory {:screen-name Str})))

(defalias FullTwitterCreds
  (HMap :mandatory {:consumer-key Str
                    :consumer-secret Str
                    :access-token Str
                    :access-secret Str}
        :complete? true))

(defalias AppOnlyTwitterCreds
  (HMap :mandatory {:consumer-key Str
                    :consumer-secret Str}
        :complete? true))

(defalias TwitterCreds
  (U FullTwitterCreds AppOnlyTwitterCreds))

(defalias TwitterAppToken
  (HMap :mandatory
        {:bearer Str}))

(defalias TwitterUserToken
  (HMap :mandatory
        {:consumer
         (HMap :mandatory
               {:key Str
                :secret Str
                :request-uri Str
                :access-uri Str
                :authorize-uri Str
                :signature-method (Val :hmac-sha1)}),
         :access-token Str
         :access-token-secret Str}) )

(defalias TwitterToken
  (U TwitterAppToken TwitterUserToken))

(defalias MultiCreds
  (t/Atom1 (t/Vec TwitterToken)))

(defalias Creds
  (U MultiCreds TwitterToken))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; twitter-rest functions
(ann ^:no-check suurvay.twitter-rest/make-oauth-creds (IFn [Str Str -> TwitterAppToken]
                                                           [Str Str Str Str -> TwitterUserToken]))
;; ^^ this is technically foreign, but it is exposed in this ns

(ann ^:no-check suurvay.twitter-rest/get-followers [Creds Identifier -> (Vec Int)])

(ann ^:no-check suurvay.twitter-rest/get-friends [Creds Identifier (U Keyword Any) * -> (Vec Int)])

(ann ^:no-check suurvay.twitter-rest/ids->names [Creds (Seq Int) -> (Seq Str)])

(ann ^:no-check suurvay.twitter-rest/names->ids [Creds (Seq Str) -> (Seq Int)])

(ann ^:no-check suurvay.twitter-rest/get-timeline-details [Creds Identifier -> (Seq Status)])

(ann ^:no-check suurvay.twitter-rest/get-timeline [Creds Identifier -> (Seq Str)])

(ann ^:no-check suurvay.twitter-rest/get-user-hashtags [Creds Identifier -> (t/Set Str)])

(ann ^:no-check suurvay.twitter-rest/get-users-hashtags [Creds (Seq Identifier) -> (t/Map Int (Seq Str))])

(ann ^:no-check suurvay.twitter-rest/get-user [Creds Identifier -> User])

(ann ^:no-check suurvay.twitter-rest/get-id [Creds Identifier -> Int])

(ann ^:no-check suurvay.twitter-rest/get-profile [Creds Identifier -> User])

(ann ^:no-check suurvay.twitter-rest/get-profile-hashtags [Creds Identifier -> (t/Set Str)])

(ann ^:no-check suurvay.twitter-rest/get-name [Creds Identifier -> Str])

(ann ^:no-check suurvay.twitter-rest/get-blocks [Creds -> (t/Set Str)])

(ann ^:no-check suurvay.twitter-rest/get-all-blocks [Creds -> (Vec Int)])

(ann ^:no-check suurvay.twitter-rest/block! [Creds Identifier -> (t/Val true)])

(ann ^:no-check suurvay.twitter-rest/unblock! [Creds Identifier -> User])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; twitter-pure functions
(ann ^:no-check suurvay.twitter-pure/num-string? [Any -> Bool])

(ann ^:no-check suurvay.twitter-pure/identifier->map [Identifier -> UserParam])

(ann ^:no-check suurvay.twitter-pure/get-hashtags [(Option Str) -> (t/Set Str)])

(ann ^:no-check suurvay.twitter-pure/timeline-hashtags [(Seq Status) -> (t/Set Str)])

(ann ^:no-check suurvay.twitter-pure/invert-frequencies [(Map Any Int) -> (Map Int (Seq Any))])

(ann ^:no-check suurvay.twitter-pure/rate-limit? [Exception -> Boolean])

(ann ^:no-check suurvay.twitter-pure/retweet-statuses [(Seq Status) -> (Seq Str)])

(ann ^:no-check suurvay.twitter-pure/retweeted-users [(Seq Status) -> (t/Set Int)])

(ann ^:no-check suurvay.twitter-pure/endorsed? [Status -> Bool])

(ann ^:no-check suurvay.twitter-pure/timeline-endorsed-hashtags [(Seq Status) -> (Seq Str)])

(ann ^:no-check suurvay.twitter-pure/faved-retweeted-users [(Seq Status) -> (t/Set Int)])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; training
(ann ^:no-check suurvay.training/sentiments [(t/Option Str) -> (t/Seq Str)])

(ann ^:no-check suurvay.training/popularity-filter [Num -> [(Map t/Any t/Num) -> Bool]])

(ann ^:no-check suurvay.training/confidence->count [Num (t/Seq t/Any) -> t/Num])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; identification

(t/ann-protocol TwitterAPI
                get-name             [TwitterAPI Identifier -> Str]
                get-profile          [TwitterAPI Identifier -> User]
                get-timeline-details [TwitterAPI Identifier -> (Seq Status)]
                get-friends          [TwitterAPI Identifier -> (Seq Int)]
                get-followers        [TwitterAPI Identifier -> (Seq Int)])

(ann suurvay.identification/twitter-api [Creds -> TwitterAPI])

;; TODO: this could have a much more rigorous signature
(defalias TestOrder
  (HVec [(HVec [(Val :before) (Val identity)])
         (HVec [(Val :real-name) [Identifier -> Str]])
         (HVec [(Val :profile) [Identifier -> User]])
         (HVec [(Val :timeline) [Identifier -> (Seq Status)]])
         (HVec [(Val :friends) [Identifier -> (Seq Int)]])
         (HVec [(Val :followers) [Identifier -> (Seq Int)]])]))

(ann ^:no-check suurvay.identification/get-twitter-fns [TwitterAPI -> TestOrder])

(defalias TestMap
  (HMap :mandatory
        {:limit Number}
        :optional
        {:before    [Identifier -> Number]
         :real-name [Str -> Number]
         :profile   [User -> Number]
         :timeline  [(Seq Status) -> Number]
         :friends   [(Seq Int) -> Number]
         :followers [(Seq Int) -> Number]}))

(ann ^:no-check suurvay.identification/score-user [TwitterAPI TestMap Identifier -> Number])

(ann ^:no-check suurvay.identification/count-hits [(t/Seqable t/Any) (t/Seqable t/Any) -> t/Num])

(ann ^:no-check suurvay.identification/regex-hits [(t/Seqable java.util.regex.Pattern) Str -> t/Num])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; streaming-twitter functions
(ann ^:no-check suurvay.streaming-twitter/filter-stream [FullTwitterCreds (Chan Status) (t/NonEmptyVec Str) -> (Map Keyword Any)])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Foreign function annotations
(ann ^:no-check twitter.oauth/make-oauth-creds (IFn [Str Str -> TwitterAppToken]
                                                    [Str Str Str Str -> TwitterUserToken]))
