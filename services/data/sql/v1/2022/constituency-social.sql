select mps.constituency,
       concat(mps.first_name, ' ', mps.last_name) as mp_name,
       mps.party                                  as mp_party,
       mps.uri                                    as mp_uri,
       mps_twitter.screen_name                    as twitter_handle
from mps
         left join mps_twitter on mps.constituency = mps_twitter.constituency
order by constituency