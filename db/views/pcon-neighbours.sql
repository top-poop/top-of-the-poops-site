drop materialized view if exists pcon_neighbours;

create materialized view pcon_neighbours as
SELECT l.pcon24nm,
       l.pcon24cd,
       neighbour.name     AS neighbour,
       neighbour.code     AS neighbour_code,
       neighbour.distance AS distance
FROM pcon_simplified l
         cross join lateral (
    select n.pcon24nm                        as name,
           n.pcon24cd                        as code,
           l.wkb_geometry <-> n.wkb_geometry as distance
    from pcon_simplified n
    where n.pcon24nm != l.pcon24nm
      and l.wkb_geometry <-> n.wkb_geometry < 0.002
    order by distance
    limit 10
    ) neighbour;
