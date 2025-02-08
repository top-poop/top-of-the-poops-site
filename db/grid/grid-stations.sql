create materialized view rainfall_station_grid as
with geometry as (select st_xmin(box2d(wkb_geometry))                                as xmin,
                         st_xmax(box2d(wkb_geometry))                                as xmax,
                         st_ymin(box2d(wkb_geometry))                                as ymin,
                         st_ymax(box2d(wkb_geometry))                                as ymax,
                         st_xmax(box2d(wkb_geometry)) - st_xmin(box2d(wkb_geometry)) as width,
                         st_ymax(box2d(wkb_geometry)) - st_ymin(box2d(wkb_geometry)) as height,
                         wkb_geometry                                                as geom
                  from ctry_dec_2022_uk_buc
                  where ctry22nm = 'England'),
     grid as (SELECT row_number() over (order by gcol, grow) as id, gcol, grow, geom
              FROM ST_RegularGrid(
                      (select geom from geometry),
                      (select width / 20 from geometry)::numeric,
                      (select height / 20 from geometry)::numeric,
                      FALSE))
select grid.id,
       rs.station_id,
       st_xmin(st_extent(geom)),
       st_xmax(st_extent(geom)),
       st_ymin(st_extent(geom)),
       st_ymax(st_extent(geom)),
       geom
from rainfall_stations rs,
     grid
where st_within(point, geom)
group by grid.id, rs.station_id, grid.geom;
