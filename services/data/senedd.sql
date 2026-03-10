with settings as (select 0.0005 as tolerance)
select english_na                                                           as name,
       st_npoints(wkb_geometry)                                           as points_original,
       st_npoints(st_simplifypreservetopology(wkb_geometry, tolerance))   as points_reduced,
       st_asgeojson(st_forcepolygoncw(st_simplifypreservetopology(wkb_geometry, tolerance))) as geometry
from senedd_final_2026 con
    full join settings on 1=1

