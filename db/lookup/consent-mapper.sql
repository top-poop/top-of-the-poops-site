
drop table if exists consent_map;

create table if not exists consent_map(
    company_name text,
    consent_id text,
    reference_consent_id text
);

create unique index if not exists  consent_map_idx_uq on consent_map ( company_name, consent_id );

insert into consent_map (company_name, consent_id, reference_consent_id)
select company_name, consent_id, consent_id from edm_consent_view
on conflict DO NOTHING;

insert into consent_map values ( 'Thames Water', 'CNTD.0073', 'CTCR.1974' );
insert into consent_map values ( 'Thames Water', 'CSSC.2453', 'TEMP.2351' );
insert into consent_map values ( 'Thames Water', 'CNTD.0021', 'CTCR.1867' );
insert into consent_map values ( 'Thames Water', 'CATM.3524', 'CATM.3525' );
insert into consent_map values ( 'Thames Water', 'CATM.3074', 'CATM.3075' );
insert into consent_map values ( 'Thames Water', 'CNTD.0004', 'TEMP.2425' );
insert into consent_map values ( 'Thames Water', 'CTCR.1766', 'TEMP.2460' );
insert into consent_map values ( 'Thames Water', 'CSSC.2465', 'TEMP.2462' );
insert into consent_map values ( 'Thames Water', 'CNTD.0082', 'TEMP.2474' );
insert into consent_map values ( 'Thames Water', 'CNTD.0028', 'TEMP.2480' );
insert into consent_map values ( 'Thames Water', 'CNTW.0360', 'TEMP.2485' );
insert into consent_map values ( 'Thames Water', 'CSSC.1001', 'TEMP.2488' );
insert into consent_map values ( 'Thames Water', 'CSSC.2347', 'TEMP.2594' );
insert into consent_map values ( 'Thames Water', 'CSSC.2350', 'TEMP.2619' );
insert into consent_map values ( 'Thames Water', 'CNTD.0066', 'TEMP.2647' );
insert into consent_map values ( 'Thames Water', 'CTCR.1313', 'TEMP.2692' );
insert into consent_map values ( 'Thames Water', 'CSSC.2339', 'TEMP.2702' );
insert into consent_map values ( 'Thames Water', 'CSSC.1402', 'TEMP.2721' );
insert into consent_map values ( 'Thames Water', 'CTCR.1781', 'TEMP.2727' );
insert into consent_map values ( 'Thames Water', 'CNTD.0084', 'TEMP.2735' );
insert into consent_map values ( 'Thames Water', 'CTCR.2136', 'TEMP.2809' );
insert into consent_map values ( 'Thames Water', 'CLCR.0165', 'TEMP.2910' );
insert into consent_map values ( 'Thames Water', 'EPR/FB3198EZ', 'EPRFB3198EZ' );
insert into consent_map values ( 'Thames Water', 'CSCC.2327', 'CSSC.2327' );
insert into consent_map values ( 'Thames Water', 'CSSC.1451', 'TEMP.2991' );
insert into consent_map values ( 'Thames Water', 'CLCR.0097', 'TEMP.3001' );
insert into consent_map values ( 'Thames Water', 'CTCR.1317', 'TEMP.3016' );
insert into consent_map values ( 'Thames Water', 'CATM.3132', 'CAWM.0012' );
