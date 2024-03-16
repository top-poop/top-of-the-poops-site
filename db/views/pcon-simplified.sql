drop materialized view pcon_simplified;

create materialized view pcon_simplified as
SELECT pcon_dec_2020_uk_bfc.pcon20nm,
       pcon_dec_2020_uk_bfc.pcon20cd,
       st_npoints(pcon_dec_2020_uk_bfc.wkb_geometry) AS points_original,
       st_npoints(
               st_simplifypreservetopology(
                       pcon_dec_2020_uk_bfc.wkb_geometry,
                       0.0005::double precision)
       )                                             AS points_reduced,
       st_simplifypreservetopology(
               pcon_dec_2020_uk_bfc.wkb_geometry,
               0.0005::double precision
       )                                             AS wkb_geometry
FROM pcon_dec_2020_uk_bfc;

create index pcon_simplified_wkb_geometry_geom_idx on pcon_simplified using gist (wkb_geometry);
