SELECT l.english_na,
       l.ogc_fid,
       n.english_na AS neighbour,
       n.ogc_fid AS neighbour_code
FROM senedd_final_2026 l
         JOIN senedd_final_2026 n
              ON ST_Touches(l.wkb_geometry, n.wkb_geometry)
                  AND n.ogc_fid <> l.ogc_fid;