drop materialized view if exists pcon_neighbours;

create materialized view pcon_neighbours as
SELECT l.pcon20nm,
       l.pcon20cd,
       neighbour.name     AS neighbour,
       neighbour.code     AS neighbour_code,
       neighbour.distance AS distance
FROM pcon_simplified l
         cross join lateral (
    select n.pcon20nm                        as name,
           n.pcon20cd                        as code,
           l.wkb_geometry <-> n.wkb_geometry as distance
    from pcon_simplified n
    where n.pcon20nm != l.pcon20nm
      and l.wkb_geometry <-> n.wkb_geometry < 0.002
    order by distance
    limit 10
    ) neighbour;
